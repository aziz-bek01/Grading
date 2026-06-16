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
    ARCHIVED,
    /**
     * Batch-4 terminal: a batch whose <em>transient</em> processing failures
     * (FAILED) have exhausted the bounded retry budget. NEVER re-dispatched by
     * the scheduled re-queuer. Distinct from VALIDATION_FAILED (a deterministic
     * user-data outcome — never retried) and SCAN_FAILED (a security/AV verdict).
     */
    DEAD_LETTER
}
