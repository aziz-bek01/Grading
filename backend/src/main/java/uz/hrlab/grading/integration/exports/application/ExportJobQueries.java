package uz.hrlab.grading.integration.exports.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
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

    @Transactional(readOnly = true)
    public Page<ExportJobJpaEntity> list(UUID projectId, ExportJobStatus status,
                                         ExportType type, Pageable pageable) {
        TenantContext ctx = requireRead();
        if (status != null) {
            return jobs.findAllByTenantIdAndProjectIdAndStatus(ctx.tenantId(), projectId, status, pageable);
        }
        if (type != null) {
            return jobs.findAllByTenantIdAndProjectIdAndExportType(ctx.tenantId(), projectId, type, pageable);
        }
        return jobs.findAllByTenantIdAndProjectId(ctx.tenantId(), projectId, pageable);
    }

    @Transactional(readOnly = true)
    public ExportJobJpaEntity get(UUID jobId) {
        TenantContext ctx = requireRead();
        return jobs.findByIdAndTenantId(jobId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
    }

    private TenantContext requireRead() {
        return TenantContextHolder.requireActive().requireAny(
                PermissionCodes.EXPORT_READ,
                PermissionCodes.EXPORT_REQUEST,
                PermissionCodes.REPORT_EXPORT,
                PermissionCodes.SALARY_EXPORT);
    }
}
