package uz.hrlab.grading.workflow.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;
import uz.hrlab.grading.workflow.domain.ProjectWorkflow;
import uz.hrlab.grading.workflow.domain.WorkflowStage;
import uz.hrlab.grading.workflow.infrastructure.ProjectWorkflowJpaEntity;
import uz.hrlab.grading.workflow.infrastructure.ProjectWorkflowRepository;

import java.util.UUID;

/**
 * Manual stage advance — bypasses the auto-derivation, allowing a Project
 * Manager to push the project to a later stage (e.g. skip an optional one).
 * Cannot go backwards. Requires WORKFLOW_EDIT.
 */
@Service
public class AdvanceWorkflowStageUseCase {

    private final ProjectRepository projects;
    private final ProjectWorkflowRepository workflows;
    private final WorkflowRecomputeService recompute;
    private final AbacGate abacGate;
    private final AuditService audit;

    public AdvanceWorkflowStageUseCase(ProjectRepository projects,
                                       ProjectWorkflowRepository workflows,
                                       WorkflowRecomputeService recompute,
                                       AbacGate abacGate,
                                       AuditService audit) {
        this.projects = projects;
        this.workflows = workflows;
        this.recompute = recompute;
        this.abacGate = abacGate;
        this.audit = audit;
    }

    @Transactional
    public ProjectWorkflow advance(UUID projectId, WorkflowStage targetStage) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.WORKFLOW_EDIT)) {
            throw new PermissionDeniedException();
        }
        if (targetStage == null) {
            throw new ValidationException("targetStage is required");
        }
        ProjectJpaEntity project = projects.findByIdAndTenantId(projectId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanWriteInProject(ctx, project.getId());

        // Lazily init workflow if absent
        recompute.recompute(projectId);
        ProjectWorkflowJpaEntity wf = workflows
                .findByTenantIdAndProjectId(ctx.tenantId(), projectId)
                .orElseThrow(TenantAccessDeniedException::new);

        WorkflowStage before = wf.getCurrentStage();
        if (targetStage.ordinal() < before.ordinal()) {
            throw new ValidationException("WORKFLOW_STAGE_REGRESSION_FORBIDDEN");
        }
        wf.setCurrentStage(targetStage);
        workflows.save(wf);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(projectId)
                .actorUserId(ctx.userId())
                .action(AuditAction.WORKFLOW_STAGE_ADVANCED)
                .entityType("ProjectWorkflow")
                .entityId(wf.getId())
                .reason("from=" + before + " to=" + targetStage)
                .build());

        return recompute.recompute(projectId);
    }
}
