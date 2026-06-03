package uz.hrlab.grading.workflow.api;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.workflow.application.AdvanceWorkflowStageUseCase;
import uz.hrlab.grading.workflow.application.GetProjectWorkflowQuery;
import uz.hrlab.grading.workflow.application.RecomputeWorkflowUseCase;

import java.util.UUID;

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
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class WorkflowController {

    private final GetProjectWorkflowQuery getQuery;
    private final AdvanceWorkflowStageUseCase advanceUseCase;
    private final RecomputeWorkflowUseCase recomputeUseCase;

    public WorkflowController(GetProjectWorkflowQuery getQuery,
                              AdvanceWorkflowStageUseCase advanceUseCase,
                              RecomputeWorkflowUseCase recomputeUseCase) {
        this.getQuery = getQuery;
        this.advanceUseCase = advanceUseCase;
        this.recomputeUseCase = recomputeUseCase;
    }

    @GetMapping("/workflow-progress")
    @PreAuthorize("hasAuthority('WORKFLOW_READ')")
    public WorkflowProgressResponse getProgress(@PathVariable UUID projectId) {
        return WorkflowProgressResponse.from(getQuery.get(projectId));
    }

    @PostMapping("/workflow/advance")
    @PreAuthorize("hasAuthority('WORKFLOW_EDIT')")
    public WorkflowProgressResponse advance(@PathVariable UUID projectId,
                                            @Valid @RequestBody AdvanceStageRequest req) {
        return WorkflowProgressResponse.from(
                advanceUseCase.advance(projectId, req.targetStage()));
    }

    @PostMapping("/workflow/recompute")
    @PreAuthorize("hasAuthority('WORKFLOW_READ')")
    public WorkflowProgressResponse recompute(@PathVariable UUID projectId) {
        return WorkflowProgressResponse.from(recomputeUseCase.recompute(projectId));
    }
}
