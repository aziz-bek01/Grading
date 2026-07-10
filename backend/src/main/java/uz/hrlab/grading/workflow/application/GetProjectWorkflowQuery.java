package uz.hrlab.grading.workflow.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;
import uz.hrlab.grading.workflow.domain.ProjectWorkflow;
import uz.hrlab.grading.workflow.infrastructure.ProjectWorkflowJpaEntity;
import uz.hrlab.grading.workflow.infrastructure.ProjectWorkflowRepository;
import uz.hrlab.grading.workflow.infrastructure.ProjectWorkflowStageJpaEntity;
import uz.hrlab.grading.workflow.infrastructure.ProjectWorkflowStageRepository;

import java.util.List;
import java.util.UUID;

/**
 * Loads (and lazily initializes + recomputes if missing) the workflow for a
 * project. Triggered by the {@code GET /api/v1/projects/{projectId}/workflow-progress}
 * endpoint.
 */
@Service
public class GetProjectWorkflowQuery {

    private final ProjectRepository projects;
    private final ProjectWorkflowRepository workflows;
    private final ProjectWorkflowStageRepository stages;
    private final WorkflowRecomputeService recompute;
    private final AbacGate abacGate;

    public GetProjectWorkflowQuery(ProjectRepository projects,
                                   ProjectWorkflowRepository workflows,
                                   ProjectWorkflowStageRepository stages,
                                   WorkflowRecomputeService recompute,
                                   AbacGate abacGate) {
        this.projects = projects;
        this.workflows = workflows;
        this.stages = stages;
        this.recompute = recompute;
        this.abacGate = abacGate;
    }

    @Transactional
    public ProjectWorkflow get(UUID projectId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.WORKFLOW_READ);
        ProjectJpaEntity project = projects.findByIdAndTenantId(projectId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanReadProject(ctx, project.getId(), project.getStatus());

        // BE-028 — recompute() derives the snapshot from live (cheap, indexed)
        // counts on every read for freshness, but now persists ONLY when a stage
        // or the current-stage pointer actually changed. In the steady state this
        // GET issues no writes and takes no locks; the returned snapshot is always
        // the freshly-derived state, so it can never be stale.
        return recompute.recompute(projectId);
    }

    @Transactional(readOnly = true)
    public ProjectWorkflow getCached(UUID projectId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.WORKFLOW_READ);
        ProjectJpaEntity project = projects.findByIdAndTenantId(projectId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanReadProject(ctx, project.getId(), project.getStatus());

        ProjectWorkflowJpaEntity wf = workflows
                .findByTenantIdAndProjectId(ctx.tenantId(), projectId)
                .orElseThrow(TenantAccessDeniedException::new);
        List<ProjectWorkflowStageJpaEntity> stageRows = stages
                .findAllByTenantIdAndProjectWorkflowIdOrderBySortOrderAsc(
                        ctx.tenantId(), wf.getId());
        return WorkflowRecomputeService.toDomain(wf, stageRows);
    }
}
