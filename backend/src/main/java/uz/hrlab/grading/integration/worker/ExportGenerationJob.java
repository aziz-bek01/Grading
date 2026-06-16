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
        UUID projectId = job.getProjectId();

        transition(job, ExportJobStatus.QUEUED);
        transition(job, ExportJobStatus.GENERATING);
        audit.record(AuditEvent.builder()
                .tenantId(tenantId).projectId(projectId)
                .action(AuditAction.EXPORT_GENERATING)
                .entityType("ExportJob").entityId(job.getId()).build());

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
            transition(job, ExportJobStatus.GENERATED);
            audit.record(AuditEvent.builder()
                    .tenantId(tenantId).projectId(projectId)
                    .action(AuditAction.EXPORT_GENERATED)
                    .entityType("ExportJob").entityId(job.getId())
                    .reason("size=" + bytes.length + " rows=" + result.rowCount()).build());
        } catch (RuntimeException e) {
            transition(job, ExportJobStatus.FAILED);
            audit.record(AuditEvent.builder()
                    .tenantId(tenantId).projectId(projectId)
                    .action(AuditAction.EXPORT_FAILED)
                    .entityType("ExportJob").entityId(job.getId())
                    .reason("exc=" + e.getClass().getSimpleName()).build());
        }
        jobs.save(job);
    }

    private void transition(ExportJobJpaEntity job, ExportJobStatus to) {
        ExportJobStatusTransitionPolicy.assertAllowed(job.getStatus(), to);
        job.setStatus(to);
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
