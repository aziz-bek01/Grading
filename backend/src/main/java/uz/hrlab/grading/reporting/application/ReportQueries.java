package uz.hrlab.grading.reporting.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
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

    @Transactional(readOnly = true)
    public Page<ReportJpaEntity> list(UUID projectId, ReportStatus status,
                                      ReportType type, UUID requestedBy, Pageable pageable) {
        TenantContext ctx = requireRead();
        if (requestedBy != null) {
            return reports.findAllByTenantIdAndRequestedBy(ctx.tenantId(), requestedBy, pageable);
        }
        if (status != null && projectId != null) {
            return reports.findAllByTenantIdAndProjectIdAndStatus(
                    ctx.tenantId(), projectId, status, pageable);
        }
        if (type != null && projectId != null) {
            return reports.findAllByTenantIdAndProjectIdAndReportType(
                    ctx.tenantId(), projectId, type, pageable);
        }
        if (projectId != null) {
            return reports.findAllByTenantIdAndProjectId(ctx.tenantId(), projectId, pageable);
        }
        return reports.findAllByTenantId(ctx.tenantId(), pageable);
    }

    @Transactional(readOnly = true)
    public ReportJpaEntity get(UUID id) {
        TenantContext ctx = requireRead();
        return reports.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
    }

    private TenantContext requireRead() {
        return TenantContextHolder.requireActive().requireAny(
                PermissionCodes.REPORT_READ,
                PermissionCodes.REPORT_CREATE,
                PermissionCodes.REPORT_EXPORT);
    }
}
