package uz.hrlab.grading.integration.imports.domain;

/**
 * Lifecycle of an Excel import batch (integration-blueprint §8.1, 13 statuses).
 *
 * <p>The {@code SCAN_FAILED} state is a sub-state of failure preserved as a
 * dedicated terminal because the audit/operations team needs to distinguish
 * malware/AV failures from parse/validation errors.
 */
public enum ImportBatchStatus {
    UPLOADED,
    SCANNING,
    SCAN_FAILED,
    PARSING,
    VALIDATING,
    VALIDATION_FAILED,
    READY_FOR_REVIEW,
    READY_TO_COMMIT,
    COMMITTING,
    COMMITTED,
    PARTIALLY_COMMITTED,
    FAILED,
    CANCELLED,
    ARCHIVED
}
