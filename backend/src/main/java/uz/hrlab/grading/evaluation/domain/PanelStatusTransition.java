package uz.hrlab.grading.evaluation.domain;

/**
 * Named transitions on the {@link EvaluationPanelStatus} state machine. Each one
 * is a single, intentional move; the {@link PanelStatusTransitionPolicy} owns
 * which {@code (current → transition)} pairs are legal.
 */
public enum PanelStatusTransition {
    /** Roster locked, per-evaluator sheets created: {@code COLLECTING → AWAITING_EVALUATIONS}. */
    LOCK_ROSTER,
    /** Averaging materialized: {@code AWAITING_EVALUATIONS → AVERAGED}. */
    AVERAGE,
    /** Sent to the CEO: {@code AVERAGED → SUBMITTED}. */
    SUBMIT_TO_CEO,
    /** CEO approved: {@code SUBMITTED → APPROVED}. */
    APPROVE,
    /**
     * Non-destructive re-review: {@code SUBMITTED/AVERAGED → AWAITING_EVALUATIONS}.
     * Evaluator sheets/scores are PRESERVED, only the stale averages clear
     * (CEO CHANGES_REQUESTED + the manual reopen control).
     */
    REOPEN_TO_AWAITING,
    /**
     * DESTRUCTIVE reset to draft: {@code SUBMITTED → COLLECTING}. Per-evaluator
     * sheets + their scores are DELETED and the panel becomes editable again so a
     * fresh roster can be locked (CEO REJECT — product-confirmed, scores lost).
     */
    RESET_TO_COLLECTING,
    /** Soft cancel: working statuses {@code → ARCHIVED}. */
    ARCHIVE
}
