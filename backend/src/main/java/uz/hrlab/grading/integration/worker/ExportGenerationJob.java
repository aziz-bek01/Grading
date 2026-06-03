package uz.hrlab.grading.integration.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.integration.excel.ExcelWriter;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Asynchronous export generation pipeline. The worker reloads the job from
 * the DB with tenant context, generates a tenant-scoped XLSX, stores it
 * under the canonical namespace, and updates the job status atomically.
 *
 * <p>For MVP 2 Phase 2 the generation body is a placeholder — the actual
 * data queries live per-type and are wired in Phase 3 (and salary types only
 * after security-engineer signs off on `SafeLogger` masking).
 */
@Component
public class ExportGenerationJob {

    private static final Logger log = LoggerFactory.getLogger(ExportGenerationJob.class);

    private final ExportJobRepository jobs;
    private final ObjectStorageAdapter storage;
    private final ExcelWriter writer;
    private final AuditService audit;

    public ExportGenerationJob(ExportJobRepository jobs,
                               ObjectStorageAdapter storage,
                               ExcelWriter writer,
                               AuditService audit) {
        this.jobs = jobs;
        this.storage = storage;
        this.writer = writer;
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
            // Placeholder content — Phase 3 wires real tenant-scoped queries.
            byte[] xlsx = writer.write("Export",
                    List.of("export_id", "type", "format", "generated_at"),
                    List.of(Map.of(
                            "export_id", job.getId().toString(),
                            "type", job.getExportType().name(),
                            "format", job.getFormat().name(),
                            "generated_at", OffsetDateTime.now().toString())));
            String storageKey = ObjectStoragePath.forExportResult(tenantId, projectId, job.getId(),
                    job.getFormat().name().toLowerCase());
            Map<String, String> metadata = new HashMap<>();
            metadata.put("tenant_id", tenantId.toString());
            metadata.put("project_id", projectId.toString());
            metadata.put("contains_salary_data", String.valueOf(job.isContainsSalaryData()));
            metadata.put("checksum_sha256", sha256(xlsx));
            storage.store(xlsx, storageKey, metadata);

            job.setFileStorageKey(storageKey);
            job.setFileSize((long) xlsx.length);
            job.setFileChecksum(metadata.get("checksum_sha256"));
            job.setRowCount(1);
            job.setGeneratedAt(OffsetDateTime.now());
            // Expiry per blueprint — 24h after generation for download window
            job.setExpiresAt(OffsetDateTime.now().plusHours(24));
            transition(job, ExportJobStatus.GENERATED);
            audit.record(AuditEvent.builder()
                    .tenantId(tenantId).projectId(projectId)
                    .action(AuditAction.EXPORT_GENERATED)
                    .entityType("ExportJob").entityId(job.getId())
                    .reason("size=" + xlsx.length).build());
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
