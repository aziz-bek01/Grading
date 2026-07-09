package uz.hrlab.grading.jobprofile.domain;

import uz.hrlab.grading.common.exception.DomainTransitionRejectedException;

/**
 * 409 Conflict — illegal job-profile status transition (e.g., approve a DRAFT,
 * edit an APPROVED, archive an ARCHIVED).
 */
public class JobProfileTransitionRejectedException extends DomainTransitionRejectedException {

    public JobProfileTransitionRejectedException(String safeMessage) {
        super("JOB_PROFILE_TRANSITION_REJECTED", safeMessage);
    }

    public JobProfileTransitionRejectedException(String code, String safeMessage) {
        super(code, safeMessage);
    }
}
