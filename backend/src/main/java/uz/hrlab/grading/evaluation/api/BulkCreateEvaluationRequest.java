package uz.hrlab.grading.evaluation.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Bulk-create body (Item 1, BE-1) — creates one DRAFT evaluation per row,
 * each row routed through the existing single-create use case so the ABAC
 * write-gate + duplicate guard + {@code EVALUATION_CREATED} audit fire per row
 * UNCHANGED. Snake_case on the wire (global Jackson SNAKE_CASE): {@code items},
 * {@code position_id}, {@code methodology_version_id}, {@code evaluator_user_id}.
 *
 * <p>{@code items} is {@code @NotEmpty} and capped at
 * {@link EvaluationController#MAX_PAGE_SIZE} (200) per call.
 */
public record BulkCreateEvaluationRequest(
        @NotEmpty(message = "items must contain at least one row")
        @Size(max = EvaluationController.MAX_PAGE_SIZE,
                message = "items capped at " + EvaluationController.MAX_PAGE_SIZE + " per call")
        @Valid
        List<Item> items
) {
    /**
     * One bulk-create row. {@code evaluatorUserId} optional (defaults to the
     * caller, mirroring {@link CreateEvaluationRequest}).
     */
    public record Item(
            @NotNull UUID positionId,
            @NotNull UUID methodologyVersionId,
            UUID evaluatorUserId
    ) {
    }
}
