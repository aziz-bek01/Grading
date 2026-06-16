package uz.hrlab.grading.reporting.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("workflow")
class ReportStatusTransitionPolicyTest {

    @Test
    void requestedMovesToQueuedOrCancelledOrFailed() {
        assertThat(ReportStatusTransitionPolicy.isAllowed(
                ReportStatus.REQUESTED, ReportStatus.QUEUED)).isTrue();
        assertThat(ReportStatusTransitionPolicy.isAllowed(
                ReportStatus.REQUESTED, ReportStatus.CANCELLED)).isTrue();
        assertThat(ReportStatusTransitionPolicy.isAllowed(
                ReportStatus.REQUESTED, ReportStatus.FAILED)).isTrue();
    }

    @Test
    void queuedMovesToGeneratingOrCancelledOrFailed() {
        assertThat(ReportStatusTransitionPolicy.isAllowed(
                ReportStatus.QUEUED, ReportStatus.GENERATING)).isTrue();
        assertThat(ReportStatusTransitionPolicy.isAllowed(
                ReportStatus.QUEUED, ReportStatus.FAILED)).isTrue();
    }

    @Test
    void generatingMovesToGeneratedOrFailedOrCancelled() {
        assertThat(ReportStatusTransitionPolicy.isAllowed(
                ReportStatus.GENERATING, ReportStatus.GENERATED)).isTrue();
        assertThat(ReportStatusTransitionPolicy.isAllowed(
                ReportStatus.GENERATING, ReportStatus.FAILED)).isTrue();
        assertThat(ReportStatusTransitionPolicy.isAllowed(
                ReportStatus.GENERATING, ReportStatus.CANCELLED)).isTrue();
    }

    @Test
    void generatedMovesToDownloadedOrExpired() {
        assertThat(ReportStatusTransitionPolicy.isAllowed(
                ReportStatus.GENERATED, ReportStatus.DOWNLOADED)).isTrue();
        assertThat(ReportStatusTransitionPolicy.isAllowed(
                ReportStatus.GENERATED, ReportStatus.EXPIRED)).isTrue();
    }

    @Test
    void downloadedOnlyExpires() {
        assertThat(ReportStatusTransitionPolicy.isAllowed(
                ReportStatus.DOWNLOADED, ReportStatus.EXPIRED)).isTrue();
        assertThat(ReportStatusTransitionPolicy.isAllowed(
                ReportStatus.DOWNLOADED, ReportStatus.REQUESTED)).isFalse();
    }

    @Test
    void terminalStatesAreTerminal() {
        // Batch-4: FAILED is no longer terminal (retryable); DEAD_LETTER is.
        for (ReportStatus to : ReportStatus.values()) {
            assertThat(ReportStatusTransitionPolicy.isAllowed(ReportStatus.CANCELLED, to)).isFalse();
            assertThat(ReportStatusTransitionPolicy.isAllowed(ReportStatus.EXPIRED, to)).isFalse();
            assertThat(ReportStatusTransitionPolicy.isAllowed(ReportStatus.DEAD_LETTER, to)).isFalse();
        }
    }

    // --- Batch-4 bounded-retry + dead-letter FSM ---

    @Test
    void failedIsRetryableToQueuedOrDeadLetter() {
        assertThat(ReportStatusTransitionPolicy.isAllowed(
                ReportStatus.FAILED, ReportStatus.QUEUED)).isTrue();
        assertThat(ReportStatusTransitionPolicy.isAllowed(
                ReportStatus.FAILED, ReportStatus.DEAD_LETTER)).isTrue();
        assertThat(ReportStatusTransitionPolicy.isAllowed(
                ReportStatus.FAILED, ReportStatus.GENERATED)).isFalse();
    }

    @Test
    void generatingMayDeadLetterDirectlyOnLastAttempt() {
        assertThat(ReportStatusTransitionPolicy.isAllowed(
                ReportStatus.GENERATING, ReportStatus.DEAD_LETTER)).isTrue();
    }

    @Test
    void deadLetterIsNeverReDispatched() {
        assertThat(ReportStatusTransitionPolicy.allowedTargets(ReportStatus.DEAD_LETTER)).isEmpty();
    }

    @Test
    void illegalTransitionThrows() {
        assertThatThrownBy(() -> ReportStatusTransitionPolicy.assertAllowed(
                ReportStatus.REQUESTED, ReportStatus.DOWNLOADED))
                .isInstanceOf(ReportTransitionRejectedException.class);
    }

    @Test
    void nullsAreNotAllowed() {
        assertThat(ReportStatusTransitionPolicy.isAllowed(null, ReportStatus.QUEUED)).isFalse();
        assertThat(ReportStatusTransitionPolicy.isAllowed(ReportStatus.QUEUED, null)).isFalse();
    }

    @Test
    void allNineStatusesExist() {
        // Batch-4 added DEAD_LETTER (8 -> 9).
        assertThat(ReportStatus.values()).hasSize(9);
    }
}
