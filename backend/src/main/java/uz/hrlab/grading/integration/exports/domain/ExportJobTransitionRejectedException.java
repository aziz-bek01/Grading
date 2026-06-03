package uz.hrlab.grading.integration.exports.domain;

import uz.hrlab.grading.common.exception.BaseDomainException;

public class ExportJobTransitionRejectedException extends BaseDomainException {
    public ExportJobTransitionRejectedException(ExportJobStatus from, ExportJobStatus to) {
        super("EXPORT_JOB_TRANSITION_REJECTED", "Illegal status transition: " + from + " -> " + to);
    }
}
