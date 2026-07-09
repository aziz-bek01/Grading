package uz.hrlab.grading.methodology.domain;

import uz.hrlab.grading.common.exception.DomainTransitionRejectedException;

/** 409 Conflict — invalid status transition for methodology version. */
public class MethodologyVersionTransitionRejectedException extends DomainTransitionRejectedException {

    public MethodologyVersionTransitionRejectedException(String safeMessage) {
        super("METHODOLOGY_VERSION_TRANSITION_REJECTED", safeMessage);
    }
}
