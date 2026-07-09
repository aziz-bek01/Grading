package uz.hrlab.grading.integration.exports.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.integration.exports.api.ExportJobResponse;
import uz.hrlab.grading.integration.exports.domain.ExportJobStatus;
import uz.hrlab.grading.integration.exports.domain.ExportJobStatusTransitionPolicy;
import uz.hrlab.grading.integration.exports.infrastructure.ExportJobJpaEntity;
import uz.hrlab.grading.integration.exports.infrastructure.ExportJobRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

@Service
public class CancelExportJobUseCase {

    private final ExportJobRepository jobs;
    private final AuditService audit;

    public CancelExportJobUseCase(ExportJobRepository jobs, AuditService audit) {
        this.jobs = jobs;
        this.audit = audit;
    }

    // BE-035 — returns the wire DTO (mapped in-tx), never the JpaEntity.
    @Transactional
    public ExportJobResponse cancel(UUID jobId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        ExportJobJpaEntity job = jobs.findByIdAndTenantId(jobId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        ExportJobStatusTransitionPolicy.assertAllowed(job.getStatus(), ExportJobStatus.CANCELLED);
        job.setStatus(ExportJobStatus.CANCELLED);
        audit.record(AuditEvent.builder(ctx)
                .projectId(job.getProjectId())
                .action(AuditAction.EXPORT_CANCELLED)
                .entityType("ExportJob")
                .entityId(job.getId())
                .build());
        return ExportJobResponse.from(jobs.save(job));
    }
}
