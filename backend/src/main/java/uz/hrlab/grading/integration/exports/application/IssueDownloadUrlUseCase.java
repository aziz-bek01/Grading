package uz.hrlab.grading.integration.exports.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.integration.exports.domain.ExportJobStatus;
import uz.hrlab.grading.integration.exports.infrastructure.ExportJobJpaEntity;
import uz.hrlab.grading.integration.exports.infrastructure.ExportJobRepository;
import uz.hrlab.grading.integration.storage.ObjectStorageAdapter;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Issues a signed download URL for a generated export. Re-verifies the
 * requesting user's permission AND ownership (no cross-tenant probing),
 * checks expiry, and writes EXPORT_DOWNLOADED to the audit log.
 *
 * <p>Signed URLs always carry the maximum 60-second TTL allowed by the
 * security-blueprint §6 rules (tightened from 5 minutes per MVP 2 Phase 2
 * integration review finding F1).
 */
@Service
public class IssueDownloadUrlUseCase {

    private static final Duration SIGNED_URL_TTL = Duration.ofSeconds(60);

    private final ExportJobRepository jobs;
    private final ObjectStorageAdapter storage;
    private final AuditService audit;

    public IssueDownloadUrlUseCase(ExportJobRepository jobs,
                                   ObjectStorageAdapter storage,
                                   AuditService audit) {
        this.jobs = jobs;
        this.storage = storage;
        this.audit = audit;
    }

    @Transactional
    public String issue(UUID exportJobId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        ExportJobJpaEntity job = jobs.findByIdAndTenantId(exportJobId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        String permission = ExportTypePermissions.requiredPermission(job.getExportType());
        if (!ctx.hasPermission(permission)) {
            throw new PermissionDeniedException();
        }
        if (job.getStatus() != ExportJobStatus.GENERATED
                && job.getStatus() != ExportJobStatus.DOWNLOADED) {
            throw new ValidationException("EXPORT_NOT_READY: " + job.getStatus());
        }
        if (job.getExpiresAt() != null && job.getExpiresAt().isBefore(OffsetDateTime.now())) {
            job.setStatus(ExportJobStatus.EXPIRED);
            jobs.save(job);
            throw new ValidationException("EXPORT_EXPIRED");
        }
        if (job.getFileStorageKey() == null) {
            throw new ValidationException("EXPORT_FILE_MISSING");
        }

        String url = storage.signedDownloadUrl(job.getFileStorageKey(), SIGNED_URL_TTL);

        // First download flips to DOWNLOADED and emits audit
        if (job.getStatus() == ExportJobStatus.GENERATED) {
            job.setStatus(ExportJobStatus.DOWNLOADED);
            job.setDownloadedAt(OffsetDateTime.now());
            jobs.save(job);
        }
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(job.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.EXPORT_DOWNLOADED)
                .entityType("ExportJob")
                .entityId(job.getId())
                .build());
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(job.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.FILE_DOWNLOADED)
                .entityType("ExportJob")
                .entityId(job.getId())
                .build());
        return url;
    }
}
