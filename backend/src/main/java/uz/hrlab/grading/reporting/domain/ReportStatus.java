package uz.hrlab.grading.reporting.domain;

/**
 * Lifecycle of a {@code Report} aggregate (mirrors the ExportJob FSM —
 * integration-blueprint §9.1, architecture §17 / ADR-009).
 *
 * <p>Batch-4 added the terminal {@link #DEAD_LETTER} state: a report that has
 * exhausted its bounded retries lands here and is NEVER re-dispatched.
 * {@link #FAILED} is a <em>retryable</em> resting state.
 */
public enum ReportStatus {
    REQUESTED,
    QUEUED,
    GENERATING,
    GENERATED,
    FAILED,
    DOWNLOADED,
    EXPIRED,
    CANCELLED,
    DEAD_LETTER
}
