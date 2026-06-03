package uz.hrlab.grading.integration.imports.domain;

/** Per-row lifecycle inside an {@code ImportBatch} (integration-blueprint §5.2). */
public enum ImportBatchRowStatus {
    PARSED,
    VALIDATED,
    READY_TO_COMMIT,
    COMMITTED,
    FAILED,
    SKIPPED
}
