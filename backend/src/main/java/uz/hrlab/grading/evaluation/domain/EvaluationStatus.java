package uz.hrlab.grading.evaluation.domain;

/**
 * Evaluation state machine
 * (architecture §14 row "Evaluation" + §15 Scoring engine; PRD Phase 5).
 *
 * <pre>
 *  DRAFT       — created, no scores yet
 *  INCOMPLETE  — some scores added but at least one required factor missing
 *  COMPLETE    — every required factor scored (transition to SUBMITTED allowed)
 *  SUBMITTED   — committee submitted for approval
 *  APPROVED    — HR Director / PM approved; immutable except via CALIBRATION_EDIT
 *  LOCKED      — production lock; only ARCHIVE permitted
 *  ARCHIVED    — terminal
 * </pre>
 */
public enum EvaluationStatus {
    DRAFT,
    INCOMPLETE,
    COMPLETE,
    SUBMITTED,
    APPROVED,
    LOCKED,
    ARCHIVED;

    /**
     * Pre-submission statuses — every state BEFORE the evaluation is handed to the
     * approval workflow ({@code SUBMITTED}). These are the statuses in which a hard
     * delete is permitted (see {@code DeleteEvaluationUseCase}); SUBMITTED /
     * APPROVED / LOCKED / ARCHIVED keep their audit trail and use the ARCHIVE path.
     *
     * <p>Single source of truth: both the delete use case (backend guard) and the
     * FE "Удалить" action visibility derive from this set — do NOT inline a
     * status list anywhere else.
     */
    public boolean isPreSubmission() {
        return this == DRAFT || this == INCOMPLETE || this == COMPLETE;
    }

    /** True when a hard delete is allowed in this status (alias of pre-submission). */
    public boolean isDeletable() {
        return isPreSubmission();
    }

    /**
     * Done-scoring statuses that count toward a panel's averaged result and its
     * per-evaluator breakdown. A panel evaluator's sheet contributes once scoring
     * is finished ({@code COMPLETE}), regardless of whether the evaluator also
     * hit the per-sheet "Submit" button ({@code SUBMITTED}) — in the consolidated
     * one-submit panel→CEO flow that button no longer opens a per-sheet approval,
     * so a submitted sheet must NOT silently drop out of the average. Sheets frozen
     * with the panel on CEO approval ({@code APPROVED}/{@code LOCKED}) still count.
     * {@code DRAFT}/{@code INCOMPLETE} (not finished) and {@code ARCHIVED}
     * (withdrawn) never contribute.
     *
     * <p>Single source of truth for {@code ComputePanelAverageUseCase} (averaging),
     * {@code PanelQueries.getResult} (the CEO-facing per-evaluator breakdown) and
     * {@code ApprovePanelUseCase} (the approval-time lock sweep) — do NOT inline a
     * status list in any of them.
     */
    public boolean contributesToPanelResult() {
        return this == COMPLETE || this == SUBMITTED || this == APPROVED || this == LOCKED;
    }
}
