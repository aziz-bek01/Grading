package uz.hrlab.grading.evaluation.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Bulk submit body — transitions every COMPLETE evaluation in
 * {@code evaluationIds} to SUBMITTED. Each evaluation is processed
 * individually; failures are collected and returned in the response.
 */
public record BulkSubmitRequest(
        @NotEmpty(message = "evaluationIds must contain at least one id")
        @Size(max = 500, message = "evaluationIds capped at 500 per call")
        List<UUID> evaluationIds,
        @Size(max = 4000) String reason
) {
}
