package uz.hrlab.grading.integration.exports.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.integration.exports.domain.ExportJobStatus;
import uz.hrlab.grading.integration.exports.infrastructure.ExportJobJpaEntity;
import uz.hrlab.grading.integration.exports.infrastructure.ExportJobRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resolves the same-origin, authenticated RELATIVE download URL for a generated
 * export (Batch 2 — D2 decision: backend-proxied stream, not a {@code file://}
 * URL).
 *
 * <p>Re-verifies the requesting user's permission AND tenant ownership (no
 * cross-tenant probing — {@code 404} for an unknown/other-tenant id) and that
 * the job is downloadable, then returns {@code /api/v1/exports/{id}/download}.
 * The bytes themselves are streamed by {@link DownloadExportFileUseCase} at that
 * endpoint, which is where the {@code EXPORT_DOWNLOADED}/{@code FILE_DOWNLOADED}
 * audit pair and the {@code GENERATED -> DOWNLOADED} status flip happen — so
 * merely resolving the link does NOT mark the export downloaded.
 *
 * <p>No signed token is embedded: the SPA is same-origin and fetches the URL as
 * an authenticated blob (JWT in the {@code Authorization} header), so no secret
 * leaks into browser history, proxy logs, or screenshots.
 */
@Service
public class IssueDownloadUrlUseCase {

    private static final String DOWNLOAD_PATH = "/api/v1/exports/%s/download";

    private final ExportJobRepository jobs;

    public IssueDownloadUrlUseCase(ExportJobRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
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
            throw new ValidationException("EXPORT_EXPIRED");
        }
        if (job.getFileStorageKey() == null) {
            throw new ValidationException("EXPORT_FILE_MISSING");
        }
        return String.format(DOWNLOAD_PATH, job.getId());
    }
}
