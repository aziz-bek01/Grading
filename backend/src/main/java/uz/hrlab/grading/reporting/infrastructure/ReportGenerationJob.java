package uz.hrlab.grading.reporting.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.Timer;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.metrics.WorkerMetrics;
import uz.hrlab.grading.integration.storage.ObjectStorageAdapter;
import uz.hrlab.grading.integration.storage.ObjectStoragePath;
import uz.hrlab.grading.integration.worker.WorkerRetryPolicy;
import uz.hrlab.grading.reporting.application.template.ReportGenerationContext;
import uz.hrlab.grading.reporting.application.template.ReportTemplate;
import uz.hrlab.grading.reporting.application.template.ReportTemplateRegistry;
import uz.hrlab.grading.reporting.domain.ReportStatus;
import uz.hrlab.grading.reporting.domain.ReportStatusTransitionPolicy;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Async report generation worker (architecture §17 / ADR-009).
 *
 * <p>Lifecycle: REQUESTED → QUEUED → GENERATING → GENERATED / FAILED. The
 * worker reloads the row with tenant context, picks the matching
 * {@link ReportTemplate}, renders to a byte buffer, stores it under the
 * canonical tenant/project namespace, and persists fingerprint + expiry.
 *
 * <p>Batch-4: failure path increments {@code attempt_count}, writes
 * {@code REPORT_FAILED} audit, and applies the shared {@link WorkerRetryPolicy}
 * — under the bound it rests in retryable {@link ReportStatus#FAILED} with an
 * exponential {@code next_attempt_at}; at the bound it lands in the terminal
 * {@link ReportStatus#DEAD_LETTER} (never re-dispatched). The scheduled
 * {@code WorkerReQueuer} re-dispatches retryable rows; the worker is the
 * idempotent claim point (FAILED → QUEUED → GENERATING under one tx +
 * optimistic {@code @Version}). The output object key is deterministic in the
 * report id, so a retry overwrites its own file — no orphans, no duplicates.
 */
@Component
public class ReportGenerationJob {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationJob.class);
    private static final int MAX_ATTEMPTS = WorkerRetryPolicy.MAX_ATTEMPTS;

    private final ReportRepository reports;
    private final ObjectStorageAdapter storage;
    private final ReportTemplateRegistry templates;
    private final AuditService audit;
    private final WorkerMetrics metrics;

    public ReportGenerationJob(ReportRepository reports,
                               ObjectStorageAdapter storage,
                               ReportTemplateRegistry templates,
                               AuditService audit,
                               WorkerMetrics metrics) {
        this.reports = reports;
        this.storage = storage;
        this.templates = templates;
        this.audit = audit;
        this.metrics = metrics;
    }

    @Async("reportWorkerExecutor")
    @Transactional
    public void generate(UUID reportId, UUID tenantId) {
        if (tenantId == null) {
            log.warn("ReportGenerationJob: missing tenant context, dropping {}", reportId);
            return;
        }
        ReportJpaEntity report = reports.findByIdAndTenantId(reportId, tenantId).orElse(null);
        if (report == null) {
            log.warn("ReportGenerationJob: report {} not found for tenant {}", reportId, tenantId);
            return;
        }
        // Defence-in-depth idempotency guard: a terminal or budget-exhausted row
        // must NOT be re-processed even if a stale dispatch arrives.
        if (report.getStatus() == ReportStatus.DEAD_LETTER
                || !WorkerRetryPolicy.canRetry(report.getAttemptCount())) {
            log.warn("ReportGenerationJob: report {} not retryable (status={}, attempts={}), skipping",
                    reportId, report.getStatus(), report.getAttemptCount());
            return;
        }
        UUID projectId = report.getProjectId();
        report.incrementAttempt();

        // Observability (Batch 6): STARTED counter + generation Timer (type tag only).
        metrics.started(WorkerMetrics.JobType.REPORT);
        Timer.Sample timer = metrics.startTimer();

        transition(report, ReportStatus.QUEUED);
        transition(report, ReportStatus.GENERATING);
        audit.record(AuditEvent.builder()
                .tenantId(tenantId).projectId(projectId)
                .action(AuditAction.REPORT_GENERATING)
                .entityType("Report").entityId(report.getId()).build());

        try {
            ReportTemplate template = templates.require(report.getReportType(), report.getFormat());
            ReportGenerationContext ctx = ReportGenerationContext.builder()
                    .reportId(report.getId())
                    .tenantId(tenantId)
                    .projectId(projectId)
                    .reportType(report.getReportType())
                    .format(report.getFormat())
                    .locale(report.getLocale())
                    .filterParams(report.getFilterParams())
                    .requestedBy(report.getRequestedBy())
                    .requestedAt(report.getRequestedAt())
                    .title(report.getTitle())
                    .build();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            template.render(ctx, buffer);
            byte[] bytes = buffer.toByteArray();

            String ext = switch (report.getFormat()) {
                case PDF -> "pdf";
                case DOCX -> "docx";
                case XLSX -> "xlsx";
            };
            String storageKey = ObjectStoragePath.forExportResult(
                    tenantId, projectId, report.getId(), ext);
            Map<String, String> metadata = new HashMap<>();
            metadata.put("tenant_id", tenantId.toString());
            metadata.put("project_id", projectId.toString());
            metadata.put("report_type", report.getReportType().name());
            metadata.put("contains_salary_data", String.valueOf(report.isContainsSalaryData()));
            String checksum = sha256(bytes);
            metadata.put("checksum_sha256", checksum);
            storage.store(bytes, storageKey, metadata);

            report.setFileStorageKey(storageKey);
            report.setFileSize((long) bytes.length);
            report.setFileChecksum(checksum);
            report.setGeneratedAt(OffsetDateTime.now());
            report.setExpiresAt(OffsetDateTime.now().plusHours(24));
            // Success clears retry bookkeeping so the re-queuer never re-selects it.
            report.setNextAttemptAt(null);
            report.setFailureReason(null);
            transition(report, ReportStatus.GENERATED);
            metrics.succeeded(WorkerMetrics.JobType.REPORT);
            audit.record(AuditEvent.builder()
                    .tenantId(tenantId).projectId(projectId)
                    .action(AuditAction.REPORT_GENERATED)
                    .entityType("Report").entityId(report.getId())
                    .reason("size=" + bytes.length).build());
        } catch (RuntimeException e) {
            handleFailure(report, tenantId, projectId, e);
        } finally {
            metrics.stopTimer(timer, WorkerMetrics.JobType.REPORT);
        }
        reports.save(report);
    }

    /**
     * Bounded-retry decision (Batch-4). attempt_count was already incremented on
     * entry. Under the bound → retryable FAILED + exponential backoff; at/over
     * the bound → terminal DEAD_LETTER. Exactly ONE REPORT_FAILED audit per
     * attempt; the dead-letter transition emits an additional terminal event.
     */
    private void handleFailure(ReportJpaEntity report, UUID tenantId, UUID projectId, RuntimeException e) {
        report.setFailureReason(safeReason(e));
        audit.record(AuditEvent.builder()
                .tenantId(tenantId).projectId(projectId)
                .action(AuditAction.REPORT_FAILED)
                .entityType("Report").entityId(report.getId())
                .reason("exc=" + e.getClass().getSimpleName() + " attempt=" + report.getAttemptCount()).build());

        if (WorkerRetryPolicy.canRetry(report.getAttemptCount())) {
            report.setNextAttemptAt(WorkerRetryPolicy.nextAttemptAt(OffsetDateTime.now(), report.getAttemptCount()));
            transition(report, ReportStatus.FAILED);
            metrics.failed(WorkerMetrics.JobType.REPORT);
            log.warn("ReportGenerationJob: report {} failed attempt {} — retry due at {}",
                    report.getId(), report.getAttemptCount(), report.getNextAttemptAt());
        } else {
            report.setNextAttemptAt(null);
            transition(report, ReportStatus.DEAD_LETTER);
            metrics.deadLetter(WorkerMetrics.JobType.REPORT);
            audit.record(AuditEvent.builder()
                    .tenantId(tenantId).projectId(projectId)
                    .action(AuditAction.REPORT_DEAD_LETTER)
                    .entityType("Report").entityId(report.getId())
                    .reason("attempts=" + report.getAttemptCount() + " max=" + MAX_ATTEMPTS).build());
            log.error("ReportGenerationJob: report {} DEAD_LETTER after {} attempts",
                    report.getId(), report.getAttemptCount());
        }
    }

    private void transition(ReportJpaEntity report, ReportStatus to) {
        ReportStatusTransitionPolicy.assertAllowed(report.getStatus(), to);
        report.setStatus(to);
    }

    /** Sanitize the failure reason — never leak tenant/PII data. */
    private static String safeReason(Throwable t) {
        String name = t.getClass().getSimpleName();
        String msg = t.getMessage();
        if (msg == null) return name;
        // Trim to 256 to fit column; do NOT include stack traces / SQL fragments.
        return (name + ": " + msg).substring(0, Math.min(256, msg.length() + name.length() + 2));
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
