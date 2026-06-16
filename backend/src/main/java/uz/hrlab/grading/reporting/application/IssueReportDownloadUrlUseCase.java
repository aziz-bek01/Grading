package uz.hrlab.grading.reporting.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.reporting.domain.ReportStatus;
import uz.hrlab.grading.reporting.infrastructure.ReportJpaEntity;
import uz.hrlab.grading.reporting.infrastructure.ReportRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resolves the same-origin, authenticated RELATIVE download URL for a generated
 * report (Batch 2 — D2 decision, mirroring the export change). Re-verifies
 * {@code REPORT_EXPORT} + tenant ownership (404 for cross-tenant) and that the
 * report is downloadable, then returns {@code /api/v1/reports/{id}/download}.
 * The bytes are streamed by {@link DownloadReportFileUseCase} at that endpoint
 * (where the download audit + status flip happen). No signed token in the URL —
 * the same-origin SPA fetches it as an authenticated blob.
 */
@Service
public class IssueReportDownloadUrlUseCase {

    private static final String DOWNLOAD_PATH = "/api/v1/reports/%s/download";

    private final ReportRepository reports;

    public IssueReportDownloadUrlUseCase(ReportRepository reports) {
        this.reports = reports;
    }

    @Transactional(readOnly = true)
    public String issue(UUID reportId) {
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
            throw new ValidationException("REPORT_EXPIRED");
        }
        if (report.getFileStorageKey() == null) {
            throw new ValidationException("REPORT_FILE_MISSING");
        }
        return String.format(DOWNLOAD_PATH, report.getId());
    }
}
