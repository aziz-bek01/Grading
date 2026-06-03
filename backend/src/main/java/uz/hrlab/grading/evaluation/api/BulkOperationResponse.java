package uz.hrlab.grading.evaluation.api;

import java.util.List;
import java.util.UUID;

/**
 * Bulk operation response — counts + per-failure detail rows. {@code updated}
 * is the number of evaluations that succeeded; {@code failed} carries one
 * entry per skipped evaluation with a stable error code (e.g.
 * IMMUTABLE_STATUS, NOT_FOUND_OR_CROSS_TENANT, PERMISSION_DENIED,
 * VALIDATION) and a short human-readable message.
 */
public record BulkOperationResponse(
        int updated,
        int skipped,
        List<Failure> failed
) {
    public record Failure(UUID evaluationId, String errorCode, String message) {
    }
}
