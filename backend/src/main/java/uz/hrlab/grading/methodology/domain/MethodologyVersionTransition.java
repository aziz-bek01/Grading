package uz.hrlab.grading.methodology.domain;

/** Allowed transitions on the {@link MethodologyVersionStatus} state machine. */
public enum MethodologyVersionTransition {
    APPROVE,
    LOCK,
    ARCHIVE,
    CREATE_NEW_VERSION
}
