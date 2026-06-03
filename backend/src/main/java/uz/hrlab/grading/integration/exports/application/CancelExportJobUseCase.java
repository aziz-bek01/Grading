package uz.hrlab.grading.integration.exports.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
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

    @Transactional
    public ExportJobJpaEntity cancel(UUID jobId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        ExportJobJpaEntity job = jobs.findByIdAndTenantId(jobId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        ExportJobStatusTransitionPolicy.assertAllowed(job.getStatus(), ExportJobStatus.CANCELLED);
        job.setStatus(ExportJobStatus.CANCELLED);
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(job.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.EXPORT_CANCELLED)
                .entityType("ExportJob")
                .entityId(job.getId())
                .build());
        return jobs.save(job);
    }
}
