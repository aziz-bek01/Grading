package uz.hrlab.grading.integration.exports.domain;

import uz.hrlab.grading.common.exception.DomainTransitionRejectedException;

public class ExportJobTransitionRejectedException extends DomainTransitionRejectedException {
    public ExportJobTransitionRejectedException(ExportJobStatus from, ExportJobStatus to) {
        super("EXPORT_JOB_TRANSITION_REJECTED", "Illegal status transition: " + from + " -> " + to);
    }
}
