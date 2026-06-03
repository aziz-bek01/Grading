package uz.hrlab.grading.integration.imports.domain;

import uz.hrlab.grading.common.exception.BaseDomainException;

/**
 * Raised when callers attempt an illegal {@link ImportBatchStatus} transition
 * (integration-blueprint §8.1). Sanitized message — never leak tenant data.
 */
public class ImportBatchTransitionRejectedException extends BaseDomainException {

    public ImportBatchTransitionRejectedException(ImportBatchStatus from, ImportBatchStatus to) {
        super("IMPORT_BATCH_TRANSITION_REJECTED",
                "Illegal status transition: " + from + " -> " + to);
    }
}
