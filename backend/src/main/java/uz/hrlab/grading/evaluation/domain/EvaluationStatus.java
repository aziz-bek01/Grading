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
    ARCHIVED
}
