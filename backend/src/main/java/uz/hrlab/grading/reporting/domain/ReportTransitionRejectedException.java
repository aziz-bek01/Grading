package uz.hrlab.grading.reporting.domain;

import uz.hrlab.grading.common.exception.DomainTransitionRejectedException;

/** Thrown when a {@link ReportStatus} transition is not allowed by the FSM. */
public class ReportTransitionRejectedException extends DomainTransitionRejectedException {

    public ReportTransitionRejectedException(ReportStatus from, ReportStatus to) {
        super("REPORT_TRANSITION_REJECTED",
                "Cannot transition report from " + from + " to " + to);
    }
}
