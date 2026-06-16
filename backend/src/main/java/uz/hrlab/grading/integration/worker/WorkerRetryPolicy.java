package uz.hrlab.grading.integration.worker;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Uniform bounded-retry + dead-letter policy shared by the import, export and
 * report async workers (Batch-4, decision D6 — keep the in-process executor,
 * add a {@code @Scheduled} re-queuer; no message broker).
 *
 * <p>The contract is a small, pure value object so the worker bodies, the
 * re-queuer, and the unit tests all agree on the exact same numbers:
 * <ul>
 *   <li><b>MAX_ATTEMPTS = 3</b> — an attempt is counted on the worker BEFORE it
 *       runs the generation body. After the 3rd failed attempt the row is
 *       terminal (DEAD_LETTER) and never re-dispatched.</li>
 *   <li><b>Backoff</b> = exponential, {@code BASE * 2^(attemptCount-1)} capped at
 *       {@code MAX_BACKOFF}. attempt 1 → 1×BASE, attempt 2 → 2×BASE, attempt 3 is
 *       the last (no further next_attempt_at is set — it dead-letters instead).</li>
 * </ul>
 *
 * <p>This class holds NO state and touches NO tenant data — it is safe to call
 * from any layer.
 */
public final class WorkerRetryPolicy {

    /** Total attempts before a row is dead-lettered (inclusive of the first). */
    public static final int MAX_ATTEMPTS = 3;

    /** Base backoff unit; attempt N waits {@code BASE * 2^(N-1)}. */
    public static final Duration BASE_BACKOFF = Duration.ofMinutes(1);

    /** Hard ceiling so a high attempt count cannot push the next attempt far out. */
    public static final Duration MAX_BACKOFF = Duration.ofMinutes(30);

    private WorkerRetryPolicy() { }

    /** True when {@code attemptCount} (the count AFTER the failing attempt) is still under the bound. */
    public static boolean canRetry(int attemptCount) {
        return attemptCount < MAX_ATTEMPTS;
    }

    /**
     * Exponential backoff for the just-failed attempt. {@code attemptCount} is
     * the value AFTER incrementing for the failing attempt (1-based): attempt 1
     * → BASE, attempt 2 → 2×BASE, capped at {@link #MAX_BACKOFF}.
     */
    public static Duration backoffFor(int attemptCount) {
        int shift = Math.max(0, attemptCount - 1);
        // Cap the shift defensively so 1L << shift never overflows.
        long multiplier = shift >= 20 ? (1L << 20) : (1L << shift);
        Duration d = BASE_BACKOFF.multipliedBy(multiplier);
        return d.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : d;
    }

    /** {@code now + backoffFor(attemptCount)} — the earliest re-dispatch time. */
    public static OffsetDateTime nextAttemptAt(OffsetDateTime now, int attemptCount) {
        return now.plus(backoffFor(attemptCount));
    }
}
