package uz.hrlab.grading.methodology.domain;

import uz.hrlab.grading.common.exception.BaseDomainException;

/** 409 Conflict — invalid status transition for methodology version. */
public class MethodologyVersionTransitionRejectedException extends BaseDomainException {

    public MethodologyVersionTransitionRejectedException(String safeMessage) {
        super("METHODOLOGY_VERSION_TRANSITION_REJECTED", safeMessage);
    }
}
