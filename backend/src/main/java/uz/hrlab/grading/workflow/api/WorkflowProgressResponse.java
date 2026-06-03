package uz.hrlab.grading.workflow.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import uz.hrlab.grading.workflow.domain.ProjectWorkflow;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response body for {@code GET /api/v1/projects/{projectId}/workflow-progress}.
 * Shape mirrors the frontend WorkflowStepper fixture so MSW handlers can be
 * replaced 1:1.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowProgressResponse(
        UUID id,
        UUID projectId,
        String currentStage,
        OffsetDateTime startedAt,
        OffsetDateTime archivedAt,
        List<WorkflowStageResponse> stages
) {
    public static WorkflowProgressResponse from(ProjectWorkflow w) {
        return new WorkflowProgressResponse(
                w.id(), w.projectId(), w.currentStage().name(),
                w.startedAt(), w.archivedAt(),
                w.stages().stream().map(WorkflowStageResponse::from).toList());
    }
}
