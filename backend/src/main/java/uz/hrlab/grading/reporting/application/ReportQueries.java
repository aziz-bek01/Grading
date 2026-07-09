package uz.hrlab.grading.reporting.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.reporting.api.ReportResponse;
import uz.hrlab.grading.reporting.domain.ReportStatus;
import uz.hrlab.grading.reporting.domain.ReportType;
import uz.hrlab.grading.reporting.infrastructure.ReportJpaEntity;
import uz.hrlab.grading.reporting.infrastructure.ReportRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

@Service
public class ReportQueries {

    private final ReportRepository reports;

    public ReportQueries(ReportRepository reports) { this.reports = reports; }

    // BE-035 — returns the wire DTO (mapped in-tx), not the JpaEntity, so the
    // persistence type never crosses into the controller. Mirrors the existing
    // EvaluationQueries.list / PanelQueries.list convention.
    @Transactional(readOnly = true)
    public Page<ReportResponse> list(UUID projectId, ReportStatus status,
                                     ReportType type, UUID requestedBy, Pageable pageable) {
        TenantContext ctx = requireRead();
        Page<ReportJpaEntity> page;
        if (requestedBy != null) {
            page = reports.findAllByTenantIdAndRequestedBy(ctx.tenantId(), requestedBy, pageable);
        } else if (status != null && projectId != null) {
            page = reports.findAllByTenantIdAndProjectIdAndStatus(
                    ctx.tenantId(), projectId, status, pageable);
        } else if (type != null && projectId != null) {
            page = reports.findAllByTenantIdAndProjectIdAndReportType(
                    ctx.tenantId(), projectId, type, pageable);
        } else if (projectId != null) {
            page = reports.findAllByTenantIdAndProjectId(ctx.tenantId(), projectId, pageable);
        } else {
            page = reports.findAllByTenantId(ctx.tenantId(), pageable);
        }
        return page.map(ReportResponse::from);
    }

    @Transactional(readOnly = true)
    public ReportResponse get(UUID id) {
        TenantContext ctx = requireRead();
        return ReportResponse.from(reports.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new));
    }

    private TenantContext requireRead() {
        return TenantContextHolder.requireActive().requireAny(
                PermissionCodes.REPORT_READ,
                PermissionCodes.REPORT_CREATE,
                PermissionCodes.REPORT_EXPORT);
    }
}
