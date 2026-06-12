package uz.hrlab.grading.evaluation.api;

import java.util.List;
import java.util.UUID;

/**
 * Bulk-create-panels response (BE-1/BE-2). A department commission = N
 * per-position panels sharing one roster; this is the failure-collector family
 * shape mirroring {@link BulkCreateEvaluationsResponse} — {@code created} is the
 * number of panels successfully opened; {@code failed} carries one row per
 * skipped/partial input keyed on {@code positionId} (snake_case
 * {@code position_id} on the wire), NOT a panel id, because a fully-failed row
 * has no panel.
 *
 * <p>Stable {@code errorCode}s (same family as bulk-create-evaluations, plus the
 * panel-specific {@code ROSTER_PARTIAL}):
 * <ul>
 *   <li>{@code ACCESS_DENIED} — cross-tenant probe OR ABAC out-of-subtree
 *       denial OR missing permission (no existence reveal);</li>
 *   <li>{@code ALREADY_EXISTS} — an active panel already exists for this
 *       (position, methodology version);</li>
 *   <li>{@code VALIDATION} / a domain code — other rejections;</li>
 *   <li>{@code ROSTER_PARTIAL} — the panel WAS created (COLLECTING) but one or
 *       more roster seats could not be assigned; {@code seatFailures} lists the
 *       per-seat reasons so the admin can fix that one panel. The row is flagged
 *       rather than silently dropped;</li>
 *   <li>{@code INTERNAL_ERROR} — unexpected failure.</li>
 * </ul>
 */
public record BulkCreatePanelsResponse(
        int created,
        List<Failure> failed
) {
    /**
     * One failed (or partially-rostered) row. {@code seatFailures} is present
     * ONLY for {@code errorCode = ROSTER_PARTIAL} (NON_NULL Jackson drops it
     * otherwise); for every other code the panel does not exist so there are no
     * seats to report.
     */
    public record Failure(
            UUID positionId,
            String errorCode,
            String message,
            List<SeatFailure> seatFailures
    ) {
        /** Fully-failed row — no panel, no seats. */
        public static Failure of(UUID positionId, String errorCode, String message) {
            return new Failure(positionId, errorCode, message, null);
        }

        /** Partial-roster row — panel created, but some seats could not be assigned. */
        public static Failure rosterPartial(UUID positionId, List<SeatFailure> seatFailures) {
            return new Failure(positionId, "ROSTER_PARTIAL",
                    "panel created but some roster seats could not be assigned",
                    seatFailures);
        }
    }

    /** One roster seat that could not be assigned on an otherwise-created panel. */
    public record SeatFailure(
            String evaluatorRole,
            UUID evaluatorUserId,
            String reason
    ) {
    }
}
