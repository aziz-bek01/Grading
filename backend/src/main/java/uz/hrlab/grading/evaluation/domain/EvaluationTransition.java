package uz.hrlab.grading.evaluation.domain;

/** Allowed transitions on the {@link EvaluationStatus} state machine. */
public enum EvaluationTransition {
    /** DRAFT/INCOMPLETE → COMPLETE or COMPLETE → INCOMPLETE — recomputed after every score change. */
    RECOMPUTE_COMPLETENESS,
    /** COMPLETE → SUBMITTED. */
    SUBMIT,
    /** SUBMITTED → APPROVED. */
    APPROVE,
    /** SUBMITTED → COMPLETE (reason required). */
    REQUEST_CHANGES,
    /** APPROVED → LOCKED. */
    LOCK,
    /** Anything except ARCHIVED → ARCHIVED (reason required). */
    ARCHIVE
}
