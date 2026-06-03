package uz.hrlab.grading.evaluation.domain;

import uz.hrlab.grading.common.exception.BaseDomainException;

/** 409 Conflict — invalid status transition / write on immutable evaluation. */
public class EvaluationTransitionRejectedException extends BaseDomainException {

    public EvaluationTransitionRejectedException(String safeMessage) {
        super("EVALUATION_TRANSITION_REJECTED", safeMessage);
    }
}
