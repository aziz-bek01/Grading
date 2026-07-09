package uz.hrlab.grading.workflow.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;
import uz.hrlab.grading.workflow.domain.ProjectWorkflow;

import java.util.UUID;

/**
 * Manual recompute trigger. Idempotent. Audited.
 */
@Service
public class RecomputeWorkflowUseCase {

    private final ProjectRepository projects;
    private final WorkflowRecomputeService recompute;
    private final AbacGate abacGate;
    private final AuditService audit;

    public RecomputeWorkflowUseCase(ProjectRepository projects,
                                    WorkflowRecomputeService recompute,
                                    AbacGate abacGate,
                                    AuditService audit) {
        this.projects = projects;
        this.recompute = recompute;
        this.abacGate = abacGate;
        this.audit = audit;
    }

    @Transactional
    public ProjectWorkflow recompute(UUID projectId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.WORKFLOW_READ);
        ProjectJpaEntity project = projects.findByIdAndTenantId(projectId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanReadProject(ctx, project.getId(), project.getStatus());

        ProjectWorkflow result = recompute.recompute(projectId);
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(projectId)
                .actorUserId(ctx.userId())
                .action(AuditAction.WORKFLOW_RECOMPUTED)
                .entityType("ProjectWorkflow")
                .entityId(result.id())
                .build());
        return result;
    }
}
