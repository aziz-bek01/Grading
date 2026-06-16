package uz.hrlab.grading.integration.worker;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests for the shared bounded-retry + backoff policy (Batch-4). */
@Tag("unit")
class WorkerRetryPolicyTest {

    @Test
    void maxAttemptsIsThree() {
        assertThat(WorkerRetryPolicy.MAX_ATTEMPTS).isEqualTo(3);
    }

    @Test
    void canRetryWhileUnderTheBound() {
        assertThat(WorkerRetryPolicy.canRetry(0)).isTrue();
        assertThat(WorkerRetryPolicy.canRetry(1)).isTrue();
        assertThat(WorkerRetryPolicy.canRetry(2)).isTrue();
        // At/over the bound -> dead-letter, never retried.
        assertThat(WorkerRetryPolicy.canRetry(3)).isFalse();
        assertThat(WorkerRetryPolicy.canRetry(4)).isFalse();
    }

    @Test
    void backoffIsExponentialFromBase() {
        // attempt 1 -> 1x BASE, attempt 2 -> 2x BASE.
        assertThat(WorkerRetryPolicy.backoffFor(1)).isEqualTo(WorkerRetryPolicy.BASE_BACKOFF);
        assertThat(WorkerRetryPolicy.backoffFor(2)).isEqualTo(WorkerRetryPolicy.BASE_BACKOFF.multipliedBy(2));
    }

    @Test
    void backoffIsCappedAtMax() {
        Duration high = WorkerRetryPolicy.backoffFor(100);
        assertThat(high).isEqualTo(WorkerRetryPolicy.MAX_BACKOFF);
        // Never exceeds the ceiling regardless of attempt count.
        assertThat(WorkerRetryPolicy.backoffFor(Integer.MAX_VALUE))
                .isLessThanOrEqualTo(WorkerRetryPolicy.MAX_BACKOFF);
    }

    @Test
    void nextAttemptAtIsNowPlusBackoff() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-16T10:00:00Z");
        assertThat(WorkerRetryPolicy.nextAttemptAt(now, 1))
                .isEqualTo(now.plus(WorkerRetryPolicy.BASE_BACKOFF));
        assertThat(WorkerRetryPolicy.nextAttemptAt(now, 1)).isAfter(now);
    }
}
