package uz.hrlab.grading.evaluation.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Bulk PATCH score body — applies the same {@code factorLevelId} to every
 * evaluation in {@code evaluationIds}. Each evaluation is processed
 * individually so partial failures (immutable status, missing permission,
 * cross-tenant probe) do not abort the entire batch.
 */
public record BulkScoreRequest(
        @NotEmpty(message = "evaluationIds must contain at least one id")
        @Size(max = 500, message = "evaluationIds capped at 500 per call")
        List<UUID> evaluationIds,
        @NotNull UUID factorLevelId,
        @Size(max = 4000) String reason
) {
}
