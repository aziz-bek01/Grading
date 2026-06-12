package uz.hrlab.grading.workflow.api;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.access.application.ActorNameResolver;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;
import uz.hrlab.grading.workflow.application.AdvanceWorkflowStageUseCase;
import uz.hrlab.grading.workflow.application.GetProjectWorkflowQuery;
import uz.hrlab.grading.workflow.application.RecomputeWorkflowUseCase;
import uz.hrlab.grading.workflow.domain.ProjectWorkflow;
import uz.hrlab.grading.workflow.domain.ProjectWorkflowStage;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Project workflow endpoints (MVP 2 Phase 1, replaces frontend MSW fixture).
 *
 * <ul>
 *   <li>{@code GET /api/v1/projects/{projectId}/workflow-progress} — the
 *       endpoint the frontend WorkflowStepper has been waiting for.</li>
 *   <li>{@code POST /api/v1/projects/{projectId}/workflow/advance} — manual
 *       stage transition by the project manager.</li>
 *   <li>{@code POST /api/v1/projects/{projectId}/workflow/recompute} — manual
 *       trigger for the recompute (idempotent).</li>
 * </ul>
 *
 * <p>BE-10 (SWEEP) — responsible_user_name + last_updated_by_name are resolved
 * server-side via the shared {@link ActorNameResolver} (no new resolver). The
 * user ids are tenant-derived (read off tenant-loaded workflow-stage rows) and
 * resolution is membership-aware + batched (one users lookup per response).
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class WorkflowController {

    private final GetProjectWorkflowQuery getQuery;
    private final AdvanceWorkflowStageUseCase advanceUseCase;
    private final RecomputeWorkflowUseCase recomputeUseCase;
    private final ActorNameResolver actorNames;

    public WorkflowController(GetProjectWorkflowQuery getQuery,
                              AdvanceWorkflowStageUseCase advanceUseCase,
                              RecomputeWorkflowUseCase recomputeUseCase,
                              ActorNameResolver actorNames) {
        this.getQuery = getQuery;
        this.advanceUseCase = advanceUseCase;
        this.recomputeUseCase = recomputeUseCase;
        this.actorNames = actorNames;
    }

    @GetMapping("/workflow-progress")
    @PreAuthorize("hasAuthority('WORKFLOW_READ')")
    public WorkflowProgressResponse getProgress(@PathVariable UUID projectId) {
        return toResponse(getQuery.get(projectId));
    }

    @PostMapping("/workflow/advance")
    @PreAuthorize("hasAuthority('WORKFLOW_EDIT')")
    public WorkflowProgressResponse advance(@PathVariable UUID projectId,
                                            @Valid @RequestBody AdvanceStageRequest req) {
        return toResponse(advanceUseCase.advance(projectId, req.targetStage()));
    }

    @PostMapping("/workflow/recompute")
    @PreAuthorize("hasAuthority('WORKFLOW_READ')")
    public WorkflowProgressResponse recompute(@PathVariable UUID projectId) {
        return toResponse(recomputeUseCase.recompute(projectId));
    }

    /** Batch-resolve every stage actor id once, then build the enriched response. */
    private WorkflowProgressResponse toResponse(ProjectWorkflow w) {
        UUID tenantId = TenantContextHolder.requireActive().tenantId();
        Set<UUID> ids = new HashSet<>();
        for (ProjectWorkflowStage s : w.stages()) {
            if (s.responsibleUserId() != null) {
                ids.add(s.responsibleUserId());
            }
            if (s.lastUpdatedBy() != null) {
                ids.add(s.lastUpdatedBy());
            }
        }
        Map<UUID, String> names = actorNames.resolveAll(tenantId, ids);
        Function<UUID, String> nameFn = id -> id == null ? null : names.get(id);
        return WorkflowProgressResponse.from(w, nameFn);
    }
}
