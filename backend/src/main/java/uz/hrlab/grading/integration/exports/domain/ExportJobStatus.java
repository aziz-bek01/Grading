package uz.hrlab.grading.integration.exports.domain;

/**
 * Lifecycle of an export job (integration-blueprint §9.1).
 *
 * <p>Batch-4 added the terminal {@link #DEAD_LETTER} state: a job that has
 * exhausted its bounded retries lands here and is NEVER re-dispatched by the
 * scheduled re-queuer. {@link #FAILED} is now a <em>retryable</em> resting
 * state (the worker may have left a {@code next_attempt_at} for backoff);
 * {@code DEAD_LETTER} is the no-loop, no-silent-drop terminal.
 */
public enum ExportJobStatus {
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
