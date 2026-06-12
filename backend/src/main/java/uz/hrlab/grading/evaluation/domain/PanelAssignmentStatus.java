package uz.hrlab.grading.evaluation.domain;

/**
 * Lifecycle of one evaluator's seat on a panel (MVP2 — multi-evaluator, DB
 * proposal §2.2).
 *
 * <pre>
 *  ASSIGNED     evaluator added to the roster; their evaluation row not yet started
 *  IN_PROGRESS  evaluator has begun scoring (their evaluation left DRAFT)
 *  COMPLETED    evaluator's evaluation reached COMPLETE — contributes to the average
 *  WITHDRAWN    evaluator removed pre-start; excluded from uniqueness + denominator
 * </pre>
 */
public enum PanelAssignmentStatus {
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    WITHDRAWN;

    /** Non-withdrawn assignments are the active roster (denominator candidates). */
    public boolean isActive() {
        return this != WITHDRAWN;
    }
}
