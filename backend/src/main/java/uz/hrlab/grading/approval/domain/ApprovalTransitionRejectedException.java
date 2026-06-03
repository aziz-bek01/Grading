package uz.hrlab.grading.approval.domain;

import uz.hrlab.grading.common.exception.BaseDomainException;

/** Thrown when an approval-related state transition violates the FSM. */
public class ApprovalTransitionRejectedException extends BaseDomainException {

    public ApprovalTransitionRejectedException(String message) {
        super("APPROVAL_TRANSITION_REJECTED", message);
    }

    public ApprovalTransitionRejectedException(String code, String message) {
        super(code, message);
    }
}
