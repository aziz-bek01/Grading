package uz.hrlab.grading.reporting.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.integration.storage.ObjectStorageAdapter;
import uz.hrlab.grading.integration.storage.ObjectStoragePath;
import uz.hrlab.grading.reporting.domain.ReportStatus;
import uz.hrlab.grading.reporting.infrastructure.ReportJpaEntity;
import uz.hrlab.grading.reporting.infrastructure.ReportRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Backend-proxied report download (Batch 2, TASK 2 — same D2 decision as exports:
 * stream stored bytes through an authenticated same-origin endpoint, not a
 * {@code file://} URL).
 *
 * <p>Gates: {@code REPORT_EXPORT} permission, tenant ownership via
 * {@code findByIdAndTenantId} (404 for cross-tenant), status
 * {@code GENERATED}/{@code DOWNLOADED}, non-expired, traversal-safe storage key.
 * Writes {@code REPORT_DOWNLOADED} + {@code FILE_DOWNLOADED} once on first
 * download.
 */
@Service
public class DownloadReportFileUseCase {

    private final ReportRepository reports;
    private final ObjectStorageAdapter storage;
    private final AuditService audit;

    public DownloadReportFileUseCase(ReportRepository reports,
                                     ObjectStorageAdapter storage,
                                     AuditService audit) {
        this.reports = reports;
        this.storage = storage;
        this.audit = audit;
    }

    /** Resolved, authorized download payload + headers the controller streams. */
    public record DownloadPayload(byte[] bytes, String filename, String contentType) { }

    @Transactional
    public DownloadPayload download(UUID reportId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.REPORT_EXPORT)) {
            throw new PermissionDeniedException();
        }
        ReportJpaEntity report = reports.findByIdAndTenantId(reportId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        if (report.getStatus() != ReportStatus.GENERATED
                && report.getStatus() != ReportStatus.DOWNLOADED) {
            throw new ValidationException("REPORT_NOT_READY: " + report.getStatus());
        }
        if (report.getExpiresAt() != null && report.getExpiresAt().isBefore(OffsetDateTime.now())) {
            report.setStatus(ReportStatus.EXPIRED);
            reports.save(report);
            throw new ValidationException("REPORT_EXPIRED");
        }
        String storageKey = report.getFileStorageKey();
        if (storageKey == null) {
            throw new ValidationException("REPORT_FILE_MISSING");
        }
        ObjectStoragePath.validate(storageKey);

        byte[] bytes = storage.retrieve(storageKey);

        if (report.getStatus() == ReportStatus.GENERATED) {
            report.setStatus(ReportStatus.DOWNLOADED);
            report.setDownloadedAt(OffsetDateTime.now());
            reports.save(report);
        }
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId()).projectId(report.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.REPORT_DOWNLOADED)
                .entityType("Report").entityId(report.getId()).build());
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId()).projectId(report.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.FILE_DOWNLOADED)
                .entityType("Report").entityId(report.getId()).build());

        return new DownloadPayload(bytes, filename(report), contentType(storageKey));
    }

    private static String filename(ReportJpaEntity report) {
        String ext = extension(report.getFileStorageKey());
        return report.getReportType().name().toLowerCase()
                + "_" + report.getId().toString().substring(0, 8) + "." + ext;
    }

    private static String extension(String storageKey) {
        int dot = storageKey == null ? -1 : storageKey.lastIndexOf('.');
        if (dot < 0 || dot == storageKey.length() - 1) return "bin";
        String ext = storageKey.substring(dot + 1).toLowerCase();
        return switch (ext) {
            case "xlsx", "pdf", "docx" -> ext;
            default -> "bin";
        };
    }

    private static String contentType(String storageKey) {
        return switch (extension(storageKey)) {
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }
}
