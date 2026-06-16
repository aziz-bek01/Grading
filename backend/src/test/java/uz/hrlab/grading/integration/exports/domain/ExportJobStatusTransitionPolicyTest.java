package uz.hrlab.grading.integration.exports.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("workflow")
class ExportJobStatusTransitionPolicyTest {

    @Test
    void requestedMovesToQueuedOrCancelledOrFailed() {
        assertThat(ExportJobStatusTransitionPolicy.isAllowed(
                ExportJobStatus.REQUESTED, ExportJobStatus.QUEUED)).isTrue();
        assertThat(ExportJobStatusTransitionPolicy.isAllowed(
                ExportJobStatus.REQUESTED, ExportJobStatus.CANCELLED)).isTrue();
        assertThat(ExportJobStatusTransitionPolicy.isAllowed(
                ExportJobStatus.REQUESTED, ExportJobStatus.FAILED)).isTrue();
    }

    @Test
    void generatingMovesToGeneratedOrFailedOrCancelled() {
        assertThat(ExportJobStatusTransitionPolicy.isAllowed(
                ExportJobStatus.GENERATING, ExportJobStatus.GENERATED)).isTrue();
        assertThat(ExportJobStatusTransitionPolicy.isAllowed(
                ExportJobStatus.GENERATING, ExportJobStatus.FAILED)).isTrue();
    }

    @Test
    void generatedMovesToDownloadedOrExpired() {
        assertThat(ExportJobStatusTransitionPolicy.isAllowed(
                ExportJobStatus.GENERATED, ExportJobStatus.DOWNLOADED)).isTrue();
        assertThat(ExportJobStatusTransitionPolicy.isAllowed(
                ExportJobStatus.GENERATED, ExportJobStatus.EXPIRED)).isTrue();
    }

    @Test
    void downloadedCanOnlyExpire() {
        assertThat(ExportJobStatusTransitionPolicy.isAllowed(
                ExportJobStatus.DOWNLOADED, ExportJobStatus.EXPIRED)).isTrue();
        assertThat(ExportJobStatusTransitionPolicy.isAllowed(
                ExportJobStatus.DOWNLOADED, ExportJobStatus.REQUESTED)).isFalse();
    }

    @Test
    void cancelledExpiredAndDeadLetterAreTerminal() {
        // Batch-4: FAILED is no longer terminal — it is the retryable resting state.
        for (ExportJobStatus to : ExportJobStatus.values()) {
            assertThat(ExportJobStatusTransitionPolicy.isAllowed(ExportJobStatus.CANCELLED, to)).isFalse();
            assertThat(ExportJobStatusTransitionPolicy.isAllowed(ExportJobStatus.EXPIRED, to)).isFalse();
            assertThat(ExportJobStatusTransitionPolicy.isAllowed(ExportJobStatus.DEAD_LETTER, to)).isFalse();
        }
    }

    // --- Batch-4 bounded-retry + dead-letter FSM ---

    @Test
    void failedIsRetryableToQueuedOrDeadLetter() {
        assertThat(ExportJobStatusTransitionPolicy.isAllowed(
                ExportJobStatus.FAILED, ExportJobStatus.QUEUED)).isTrue();
        assertThat(ExportJobStatusTransitionPolicy.isAllowed(
                ExportJobStatus.FAILED, ExportJobStatus.DEAD_LETTER)).isTrue();
        // No other targets from FAILED.
        assertThat(ExportJobStatusTransitionPolicy.isAllowed(
                ExportJobStatus.FAILED, ExportJobStatus.GENERATING)).isFalse();
        assertThat(ExportJobStatusTransitionPolicy.isAllowed(
                ExportJobStatus.FAILED, ExportJobStatus.GENERATED)).isFalse();
    }

    @Test
    void generatingMayDeadLetterDirectlyOnLastAttempt() {
        assertThat(ExportJobStatusTransitionPolicy.isAllowed(
                ExportJobStatus.GENERATING, ExportJobStatus.DEAD_LETTER)).isTrue();
    }

    @Test
    void deadLetterIsNeverReDispatched() {
        assertThat(ExportJobStatusTransitionPolicy.allowedTargets(ExportJobStatus.DEAD_LETTER)).isEmpty();
    }

    @Test
    void illegalTransitionThrows() {
        assertThatThrownBy(() -> ExportJobStatusTransitionPolicy.assertAllowed(
                ExportJobStatus.REQUESTED, ExportJobStatus.DOWNLOADED))
                .isInstanceOf(ExportJobTransitionRejectedException.class);
    }

    @Test
    void allNineStatusesExist() {
        // Batch-4 added DEAD_LETTER (8 -> 9).
        assertThat(ExportJobStatus.values()).hasSize(9);
    }
}
