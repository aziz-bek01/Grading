package uz.hrlab.grading.workflow.domain;

/**
 * Canonical 11-stage workflow (architecture §14).
 *
 * <p>Order is significant: {@code sortOrder() = ordinal() + 1}. The frontend
 * stepper renders them in declaration order. New stages must NEVER be inserted
 * in the middle — append only.
 */
public enum WorkflowStage {
    SETUP,
    ORGANIZATION,
    POSITIONS,
    JOB_PROFILES,
    METHODOLOGY,
    EVALUATION,
    CALIBRATION,
    GRADES,
    COMPENSATION,
    REPORTS,
    ARCHIVE;

    public int sortOrder() { return ordinal() + 1; }
}
