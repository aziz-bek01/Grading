package uz.hrlab.grading.evaluation.application;

import uz.hrlab.grading.evaluation.domain.EvaluatorRole;

import java.util.List;
import java.util.UUID;

/**
 * Application command for bulk-create-panels (BE-1). One shared {@code roster}
 * applied to every position in {@code positionIds}, all against
 * {@code methodologyVersionId}. Decoupled from the API DTO so the use case has
 * no web dependency (ArchitectureTest).
 *
 * <p>{@code startEvaluations} (opt-in) — when true, every panel that is created
 * with a COMPLETE roster (no seat failures) is immediately roster-locked
 * (COLLECTING → AWAITING_EVALUATIONS) so the per-evaluator DRAFT Evaluation
 * sheets exist and assigned experts see their rows in "My Evaluations". When
 * false/omitted, behaviour is UNCHANGED: panels stay COLLECTING and locking is a
 * separate manual step. The mandatory-trio + min-3 enforcement stays inside
 * {@code LockRosterUseCase}; if unmet the lock fails and the row is reported as
 * {@code ROSTER_LOCK_FAILED} (the panel still exists in COLLECTING).
 */
public record BulkCreatePanelsCommand(
        UUID methodologyVersionId,
        List<UUID> positionIds,
        List<RosterSeat> roster,
        boolean startEvaluations
) {
    /** One shared roster seat. */
    public record RosterSeat(UUID evaluatorUserId, EvaluatorRole evaluatorRole) {
    }
}
