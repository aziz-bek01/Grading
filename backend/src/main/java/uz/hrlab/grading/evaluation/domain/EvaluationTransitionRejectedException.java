package uz.hrlab.grading.evaluation.domain;

import uz.hrlab.grading.common.exception.DomainTransitionRejectedException;

/** 409 Conflict — invalid status transition / write on immutable evaluation. */
public class EvaluationTransitionRejectedException extends DomainTransitionRejectedException {

    public EvaluationTransitionRejectedException(String safeMessage) {
        super("EVALUATION_TRANSITION_REJECTED", safeMessage);
    }
}
