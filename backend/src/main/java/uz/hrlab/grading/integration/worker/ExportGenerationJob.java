package uz.hrlab.grading.integration.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.integration.exports.application.ExportContentGenerator;
import uz.hrlab.grading.integration.exports.domain.ExportJobStatus;
import uz.hrlab.grading.integration.exports.domain.ExportJobStatusTransitionPolicy;
import uz.hrlab.grading.integration.exports.infrastructure.ExportJobJpaEntity;
import uz.hrlab.grading.integration.exports.infrastructure.ExportJobRepository;
import uz.hrlab.grading.integration.storage.ObjectStorageAdapter;
import uz.hrlab.grading.integration.storage.ObjectStoragePath;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Asynchronous export generation pipeline. The worker reloads the job from
 * the DB with tenant context, generates a tenant-scoped XLSX, stores it
 * under the canonical namespace, and updates the job status atomically.
 *
 * <p>Batch-2: the generation body now produces REAL, tenant-scoped content via
 * {@link ExportContentGenerator} (sourced through the tenant + project scoped
 * {@code ReportDataPort}) in the requested {@code ExportFormat}. Salary-bearing
 * types remain structurally-stubbed (valid empty document) until the salary
 * data source ships in a later batch — see {@link ExportContentGenerator}.
 *
 * <p>Batch-4: bounded retry + dead-letter. Each invocation counts ONE attempt
 * (incremented before the body runs). On a transient failure the job goes back
 * to a retryable {@link ExportJobStatus#FAILED} with an exponential
 * {@code next_attempt_at} as long as {@code attempt_count < MAX_ATTEMPTS}; once
 * the budget is exhausted it lands in the terminal
 * {@link ExportJobStatus#DEAD_LETTER} (never re-dispatched). The
 * {@link WorkerReQueuer} re-dispatches retryable rows. Idempotency: the worker
 * is the claim point — it transitions FAILED → QUEUED → GENERATING under its own
 * tx + optimistic {@code @Version}, so a racing scheduler tick that re-loads the
 * row finds it no longer retryable (or loses the optimistic-lock write) and
 * cannot double-process. The output object key is deterministic in the job id,
 * so a retry overwrites its own file — no orphaned partials, no duplicates.
 */
@Component
public class ExportGenerationJob {

    private static final Logger log = LoggerFactory.getLogger(ExportGenerationJob.class);

    private final ExportJobRepository jobs;
    private final ObjectStorageAdapter storage;
    private final ExportContentGenerator content;
    private final AuditService audit;

    public ExportGenerationJob(ExportJobRepository jobs,
                               ObjectStorageAdapter storage,
                               ExportContentGenerator content,
                               AuditService audit) {
        this.jobs = jobs;
        this.storage = storage;
        this.content = content;
        this.audit = audit;
    }

    @Async("exportWorkerExecutor")
    @Transactional
    public void generate(UUID exportJobId, UUID tenantId) {
        if (tenantId == null) {
            log.warn("ExportGenerationJob: missing tenant context, dropping {}", exportJobId);
            return;
        }
        ExportJobJpaEntity job = jobs.findByIdAndTenantId(exportJobId, tenantId).orElse(null);
        if (job == null) {
            log.warn("ExportGenerationJob: job {} not found for tenant {}", exportJobId, tenantId);
            return;
        }
        // Defence-in-depth: a row at/over the retry bound (or already terminal)
        // must NOT be re-processed — the re-queuer filters these out, but a stale
        // dispatch could still arrive. This is the idempotency claim guard.
        if (job.getStatus() == ExportJobStatus.DEAD_LETTER
                || !WorkerRetryPolicy.canRetry(job.getAttemptCount())) {
            log.warn("ExportGenerationJob: job {} not retryable (status={}, attempts={}), skipping",
                    exportJobId, job.getStatus(), job.getAttemptCount());
            return;
        }
        UUID projectId = job.getProjectId();
        job.incrementAttempt();

        transition(job, ExportJobStatus.QUEUED);
        transition(job, ExportJobStatus.GENERATING);
        audit.record(AuditEvent.builder()
                .tenantId(tenantId).projectId(projectId)
                .action(AuditAction.EXPORT_GENERATING)
                .entityType("ExportJob").entityId(job.getId())
                .reason("attempt=" + job.getAttemptCount()).build());

        try {
            // Real, tenant-scoped content sourced through ReportDataPort (locale
            // is not modelled on the export job — methodologySpec tolerates null).
            ExportContentGenerator.GeneratedExport result = content.generate(
                    job.getExportType(), job.getFormat(), tenantId, projectId, null);
            byte[] bytes = result.bytes();
            String storageKey = ObjectStoragePath.forExportResult(tenantId, projectId, job.getId(),
                    result.extension());
            Map<String, String> metadata = new HashMap<>();
            metadata.put("tenant_id", tenantId.toString());
            metadata.put("project_id", projectId.toString());
            metadata.put("contains_salary_data", String.valueOf(job.isContainsSalaryData()));
            metadata.put("content_type", result.contentType());
            metadata.put("checksum_sha256", sha256(bytes));
            storage.store(bytes, storageKey, metadata);

            job.setFileStorageKey(storageKey);
            job.setFileSize((long) bytes.length);
            job.setFileChecksum(metadata.get("checksum_sha256"));
            job.setRowCount(result.rowCount());
            job.setGeneratedAt(OffsetDateTime.now());
            // Expiry per blueprint — 24h after generation for download window
            job.setExpiresAt(OffsetDateTime.now().plusHours(24));
            // Success clears retry bookkeeping so the re-queuer never re-selects it.
            job.setNextAttemptAt(null);
            job.setFailureReason(null);
            transition(job, ExportJobStatus.GENERATED);
            audit.record(AuditEvent.builder()
                    .tenantId(tenantId).projectId(projectId)
                    .action(AuditAction.EXPORT_GENERATED)
                    .entityType("ExportJob").entityId(job.getId())
                    .reason("size=" + bytes.length + " rows=" + result.rowCount()).build());
        } catch (RuntimeException e) {
            handleFailure(job, tenantId, projectId, e);
        }
        jobs.save(job);
    }

    /**
     * Bounded-retry decision (Batch-4). attempt_count was already incremented on
     * entry, so it reflects this just-failed attempt. Under the bound → retryable
     * FAILED with exponential backoff; at/over the bound → terminal DEAD_LETTER.
     * Exactly ONE failure audit is emitted per attempt (here, not per scheduler
     * tick); the dead-letter transition emits an additional terminal event.
     */
    private void handleFailure(ExportJobJpaEntity job, UUID tenantId, UUID projectId, RuntimeException e) {
        job.setFailureReason(safeReason(e));
        audit.record(AuditEvent.builder()
                .tenantId(tenantId).projectId(projectId)
                .action(AuditAction.EXPORT_FAILED)
                .entityType("ExportJob").entityId(job.getId())
                .reason("exc=" + e.getClass().getSimpleName() + " attempt=" + job.getAttemptCount()).build());

        if (WorkerRetryPolicy.canRetry(job.getAttemptCount())) {
            job.setNextAttemptAt(WorkerRetryPolicy.nextAttemptAt(OffsetDateTime.now(), job.getAttemptCount()));
            transition(job, ExportJobStatus.FAILED);
            log.warn("ExportGenerationJob: job {} failed attempt {} — retry due at {}",
                    job.getId(), job.getAttemptCount(), job.getNextAttemptAt());
        } else {
            job.setNextAttemptAt(null);
            transition(job, ExportJobStatus.DEAD_LETTER);
            audit.record(AuditEvent.builder()
                    .tenantId(tenantId).projectId(projectId)
                    .action(AuditAction.EXPORT_DEAD_LETTER)
                    .entityType("ExportJob").entityId(job.getId())
                    .reason("attempts=" + job.getAttemptCount() + " max=" + WorkerRetryPolicy.MAX_ATTEMPTS).build());
            log.error("ExportGenerationJob: job {} DEAD_LETTER after {} attempts",
                    job.getId(), job.getAttemptCount());
        }
    }

    private void transition(ExportJobJpaEntity job, ExportJobStatus to) {
        ExportJobStatusTransitionPolicy.assertAllowed(job.getStatus(), to);
        job.setStatus(to);
    }

    /** Sanitize the failure reason — never leak tenant/PII data or stack frames. */
    private static String safeReason(Throwable t) {
        String name = t.getClass().getSimpleName();
        String msg = t.getMessage();
        if (msg == null) return name;
        String combined = name + ": " + msg;
        return combined.length() > 256 ? combined.substring(0, 256) : combined;
    }

    private String sha256(byte[] data) {
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
