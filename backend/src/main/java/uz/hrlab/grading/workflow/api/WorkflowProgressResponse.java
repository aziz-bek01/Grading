package uz.hrlab.grading.workflow.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import uz.hrlab.grading.workflow.domain.ProjectWorkflow;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

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
    /** Legacy / no-name overload. */
    public static WorkflowProgressResponse from(ProjectWorkflow w) {
        return from(w, id -> null);
    }

    /**
     * BE-10 — resolved-name overload. {@code nameFn} (id → display name,
     * batch-resolved via the shared {@code ActorNameResolver}) is threaded into
     * every stage's responsible_user_name / last_updated_by_name.
     */
    public static WorkflowProgressResponse from(ProjectWorkflow w, Function<UUID, String> nameFn) {
        return new WorkflowProgressResponse(
                w.id(), w.projectId(), w.currentStage().name(),
                w.startedAt(), w.archivedAt(),
                w.stages().stream().map(s -> WorkflowStageResponse.from(s, nameFn)).toList());
    }
}
