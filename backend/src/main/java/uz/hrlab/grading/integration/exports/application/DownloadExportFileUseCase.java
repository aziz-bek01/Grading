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
import uz.hrlab.grading.integration.storage.ObjectStoragePath;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Backend-proxied export download (Batch 2, TASK 2 — the D2 decision: stream the
 * stored bytes through an authenticated same-origin endpoint, NOT a raw
 * {@code file://} URL).
 *
 * <p>Security gates, in order (defence-in-depth — the controller also carries
 * {@code @PreAuthorize}):
 * <ol>
 *   <li>active {@link TenantContext} required (no context ⇒ programming error);</li>
 *   <li>job loaded via {@code findByIdAndTenantId} — a cross-tenant id is
 *       indistinguishable from a missing id and yields {@code 404} (anti-IDOR,
 *       security-blueprint §11);</li>
 *   <li>the export-type's required permission is re-checked
 *       ({@link ExportTypePermissions}); salary-bearing types therefore require
 *       {@code SALARY_EXPORT} ⇒ {@code 403} when absent;</li>
 *   <li>job MUST be {@code GENERATED}/{@code DOWNLOADED} (a non-generated job
 *       yields {@code 400});</li>
 *   <li>file storage key MUST exist and resolve under the tenant/project
 *       namespace ({@link ObjectStoragePath#validate} — traversal-safe).</li>
 * </ol>
 *
 * <p>The bytes are resolved through the {@link ObjectStorageAdapter} port (local
 * filesystem today, MinIO later) — no {@code file://} URL ever leaves the
 * backend. A single {@code EXPORT_DOWNLOADED} + {@code FILE_DOWNLOADED} audit
 * pair is written on first download; subsequent downloads re-audit but do not
 * re-flip status.
 */
@Service
public class DownloadExportFileUseCase {

    private final ExportJobRepository jobs;
    private final ObjectStorageAdapter storage;
    private final AuditService audit;

    public DownloadExportFileUseCase(ExportJobRepository jobs,
                                     ObjectStorageAdapter storage,
                                     AuditService audit) {
        this.jobs = jobs;
        this.storage = storage;
        this.audit = audit;
    }

    /** Resolved, authorized download payload + headers the controller streams. */
    public record DownloadPayload(byte[] bytes, String filename, String contentType) { }

    @Transactional
    public DownloadPayload download(UUID exportJobId) {
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
        String storageKey = job.getFileStorageKey();
        if (storageKey == null) {
            throw new ValidationException("EXPORT_FILE_MISSING");
        }
        // Traversal-safe: the stored key must still match the tenant/project
        // namespace before we read it back (defence-in-depth — the worker built
        // it via ObjectStoragePath, but never trust a persisted string blindly).
        ObjectStoragePath.validate(storageKey);

        byte[] bytes = storage.retrieve(storageKey);

        // First download flips GENERATED -> DOWNLOADED; both states re-audit.
        if (job.getStatus() == ExportJobStatus.GENERATED) {
            job.setStatus(ExportJobStatus.DOWNLOADED);
            job.setDownloadedAt(OffsetDateTime.now());
            jobs.save(job);
        }
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId()).projectId(job.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.EXPORT_DOWNLOADED)
                .entityType("ExportJob").entityId(job.getId()).build());
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId()).projectId(job.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.FILE_DOWNLOADED)
                .entityType("ExportJob").entityId(job.getId()).build());

        return new DownloadPayload(bytes, filename(job), contentType(storageKey));
    }

    /** Safe download filename — type + short job id + extension, never user input. */
    private static String filename(ExportJobJpaEntity job) {
        String ext = extension(job.getFileStorageKey());
        return job.getExportType().name().toLowerCase()
                + "_" + shortId(job.getId()) + "." + ext;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static String extension(String storageKey) {
        int dot = storageKey == null ? -1 : storageKey.lastIndexOf('.');
        if (dot < 0 || dot == storageKey.length() - 1) return "bin";
        String ext = storageKey.substring(dot + 1).toLowerCase();
        // Allowlist — never echo an arbitrary persisted suffix into a header.
        return switch (ext) {
            case "xlsx", "csv", "pdf", "docx" -> ext;
            default -> "bin";
        };
    }

    private static String contentType(String storageKey) {
        return switch (extension(storageKey)) {
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "csv" -> "text/csv";
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }
}
