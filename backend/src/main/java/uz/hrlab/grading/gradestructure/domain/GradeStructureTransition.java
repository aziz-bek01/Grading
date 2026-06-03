package uz.hrlab.grading.gradestructure.domain;

/**
 * Allowed transitions of {@link GradeStructureStatus}. APPROVED + LOCKED are
 * immutable; mutating edits require a new version via CREATE_NEW_VERSION.
 */
public enum GradeStructureTransition {
    APPROVE,
    LOCK,
    ARCHIVE,
    CREATE_NEW_VERSION
}
