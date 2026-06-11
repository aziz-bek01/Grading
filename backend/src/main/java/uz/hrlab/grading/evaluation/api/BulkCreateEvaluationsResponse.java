package uz.hrlab.grading.evaluation.api;

import java.util.List;
import java.util.UUID;

/**
 * Bulk-create response (Item 1, BE-1). {@code created} is the number of
 * evaluations successfully created; {@code failed} carries one row per skipped
 * input with a stable {@code errorCode} (e.g. {@code ALREADY_EXISTS},
 * {@code ACCESS_DENIED}, {@code NOT_FOUND}, {@code VALIDATION},
 * {@code INTERNAL_ERROR}) and a short message.
 *
 * <p>Failure rows key on {@code positionId} (snake_case {@code position_id} on
 * the wire) — NOT an evaluation id, because no evaluation was created for a
 * failed row. This is the deliberate difference from {@link BulkOperationResponse}
 * (whose {@code Failure} keys on {@code evaluationId} for the bulk-update flows).
 */
public record BulkCreateEvaluationsResponse(
        int created,
        List<Failure> failed
) {
    public record Failure(UUID positionId, String errorCode, String message) {
    }
}
