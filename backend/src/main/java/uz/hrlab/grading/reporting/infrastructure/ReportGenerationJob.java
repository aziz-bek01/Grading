package uz.hrlab.grading.reporting.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.integration.storage.ObjectStorageAdapter;
import uz.hrlab.grading.integration.storage.ObjectStoragePath;
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
 * <p>Failure path increments {@code attempt_count}, writes
 * {@code REPORT_FAILED} audit, and stops at attempt 3 (retry policy lives in
 * a scheduled re-queuer, not the worker itself — keeps the worker idempotent).
 */
@Component
public class ReportGenerationJob {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationJob.class);
    private static final int MAX_ATTEMPTS = 3;

    private final ReportRepository reports;
    private final ObjectStorageAdapter storage;
    private final ReportTemplateRegistry templates;
    private final AuditService audit;

    public ReportGenerationJob(ReportRepository reports,
                               ObjectStorageAdapter storage,
                               ReportTemplateRegistry templates,
                               AuditService audit) {
        this.reports = reports;
        this.storage = storage;
        this.templates = templates;
        this.audit = audit;
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
        if (report.getAttemptCount() >= MAX_ATTEMPTS) {
            log.warn("ReportGenerationJob: report {} exceeded MAX_ATTEMPTS={}, leaving as-is",
                    reportId, MAX_ATTEMPTS);
            return;
        }
        UUID projectId = report.getProjectId();
        report.incrementAttempt();

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
            transition(report, ReportStatus.GENERATED);
            audit.record(AuditEvent.builder()
                    .tenantId(tenantId).projectId(projectId)
                    .action(AuditAction.REPORT_GENERATED)
                    .entityType("Report").entityId(report.getId())
                    .reason("size=" + bytes.length).build());
        } catch (RuntimeException e) {
            transition(report, ReportStatus.FAILED);
            report.setFailureReason(safeReason(e));
            audit.record(AuditEvent.builder()
                    .tenantId(tenantId).projectId(projectId)
                    .action(AuditAction.REPORT_FAILED)
                    .entityType("Report").entityId(report.getId())
                    .reason("exc=" + e.getClass().getSimpleName()).build());
            log.warn("ReportGenerationJob: report {} failed: {}", reportId, e.getClass().getSimpleName());
        }
        reports.save(report);
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
