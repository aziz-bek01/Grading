package uz.hrlab.grading.integration.exports.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.integration.exports.api.ExportJobResponse;
import uz.hrlab.grading.integration.exports.domain.ExportJobStatus;
import uz.hrlab.grading.integration.exports.domain.ExportType;
import uz.hrlab.grading.integration.exports.infrastructure.ExportJobJpaEntity;
import uz.hrlab.grading.integration.exports.infrastructure.ExportJobRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

@Service
public class ExportJobQueries {

    private final ExportJobRepository jobs;

    public ExportJobQueries(ExportJobRepository jobs) {
        this.jobs = jobs;
    }

    // BE-035 — returns the wire DTO (mapped in-tx), never the JpaEntity.
    @Transactional(readOnly = true)
    public Page<ExportJobResponse> list(UUID projectId, ExportJobStatus status,
                                        ExportType type, Pageable pageable) {
        TenantContext ctx = requireRead();
        Page<ExportJobJpaEntity> page;
        if (status != null) {
            page = jobs.findAllByTenantIdAndProjectIdAndStatus(ctx.tenantId(), projectId, status, pageable);
        } else if (type != null) {
            page = jobs.findAllByTenantIdAndProjectIdAndExportType(ctx.tenantId(), projectId, type, pageable);
        } else {
            page = jobs.findAllByTenantIdAndProjectId(ctx.tenantId(), projectId, pageable);
        }
        return page.map(ExportJobResponse::from);
    }

    @Transactional(readOnly = true)
    public ExportJobResponse get(UUID jobId) {
        TenantContext ctx = requireRead();
        return ExportJobResponse.from(jobs.findByIdAndTenantId(jobId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new));
    }

    private TenantContext requireRead() {
        return TenantContextHolder.requireActive().requireAny(
                PermissionCodes.EXPORT_READ,
                PermissionCodes.EXPORT_REQUEST,
                PermissionCodes.REPORT_EXPORT,
                PermissionCodes.SALARY_EXPORT);
    }
}
