package uz.hrlab.grading.evaluation.domain;

import uz.hrlab.grading.common.exception.BaseDomainException;

/** 409 Conflict — invalid status transition on an {@link EvaluationPanel}. */
public class PanelStatusTransitionRejectedException extends BaseDomainException {

    public PanelStatusTransitionRejectedException(String safeMessage) {
        super("PANEL_STATUS_TRANSITION_REJECTED", safeMessage);
    }
}
