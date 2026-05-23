package uz.hrlab.grading.jobprofile.domain;

/**
 * Domain action for {@link JobProfileStatusTransitionPolicy}. Maps 1:1 to the
 * use cases under {@code application}.
 */
public enum JobProfileTransition {
    SUBMIT_FOR_REVIEW,
    APPROVE,
    REQUEST_CHANGES,
    ARCHIVE,
    CREATE_REVISION
}
