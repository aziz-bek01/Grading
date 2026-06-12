package uz.hrlab.grading.evaluation.domain;

/**
 * Evaluation panel (multi-evaluator case) state machine (MVP2 — multi-evaluator,
 * DB proposal §4 + UX proposal §7).
 *
 * <pre>
 *  COLLECTING            roster being built; assignments added/removed; NOT started
 *  AWAITING_EVALUATIONS  roster locked; each evaluator scores independently
 *  AVERAGED              PanelAveragingService run; raw/displayed totals + per-factor
 *                        averages written
 *  SUBMITTED             approval_request opened on the PANEL — single CEO step
 *  APPROVED              CEO approved; grade assigned from the averaged score
 *  LOCKED                production lock; only ARCHIVE permitted
 *  ARCHIVED              terminal
 * </pre>
 */
public enum EvaluationPanelStatus {
    COLLECTING,
    AWAITING_EVALUATIONS,
    AVERAGED,
    SUBMITTED,
    APPROVED,
    LOCKED,
    ARCHIVED;

    /**
     * "Collecting" statuses — every state where the roster is still being built
     * OR evaluators are still scoring independently and the blind rule applies.
     * This is the single-source predicate the bias-isolation read guard
     * (BE-11) consults: while a panel {@code isCollecting()}, a non-result-viewer
     * evaluator may read ONLY their own sheet. Mirrors
     * {@link EvaluationStatus#isPreSubmission()} — do NOT inline a status list
     * anywhere else.
     */
    public boolean isCollecting() {
        return this == COLLECTING || this == AWAITING_EVALUATIONS;
    }

    /**
     * True while evaluators may still be added/removed (roster mutation window).
     * Used by AssignEvaluator / WithdrawEvaluator use cases (BE-5) — assignment
     * mutation is rejected once the panel moves past {@code COLLECTING}.
     */
    public boolean isRosterMutable() {
        return this == COLLECTING;
    }

    /** Terminal/committed statuses — panel may not be re-averaged or re-rostered. */
    public boolean isCommitted() {
        return this == APPROVED || this == LOCKED || this == ARCHIVED;
    }
}
