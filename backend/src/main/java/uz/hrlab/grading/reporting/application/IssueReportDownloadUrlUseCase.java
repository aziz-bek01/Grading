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
import uz.hrlab.grading.reporting.domain.ReportStatus;
import uz.hrlab.grading.reporting.infrastructure.ReportJpaEntity;
import uz.hrlab.grading.reporting.infrastructure.ReportRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Issues a signed download URL for a generated report. TTL = 60s, matching
 * the Phase 2 export tightening (integration-review F1 / security-blueprint
 * §6).
 */
@Service
public class IssueReportDownloadUrlUseCase {

    private static final Duration SIGNED_URL_TTL = Duration.ofSeconds(60);

    private final ReportRepository reports;
    private final ObjectStorageAdapter storage;
    private final AuditService audit;

    public IssueReportDownloadUrlUseCase(ReportRepository reports,
                                         ObjectStorageAdapter storage,
                                         AuditService audit) {
        this.reports = reports;
        this.storage = storage;
        this.audit = audit;
    }

    @Transactional
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
            report.setStatus(ReportStatus.EXPIRED);
            reports.save(report);
            audit.record(AuditEvent.builder()
                    .tenantId(ctx.tenantId()).projectId(report.getProjectId())
                    .actorUserId(ctx.userId())
                    .action(AuditAction.REPORT_EXPIRED)
                    .entityType("Report").entityId(report.getId()).build());
            throw new ValidationException("REPORT_EXPIRED");
        }
        if (report.getFileStorageKey() == null) {
            throw new ValidationException("REPORT_FILE_MISSING");
        }

        String url = storage.signedDownloadUrl(report.getFileStorageKey(), SIGNED_URL_TTL);

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
        return url;
    }
}
