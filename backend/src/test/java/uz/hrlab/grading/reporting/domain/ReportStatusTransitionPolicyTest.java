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
        for (ReportStatus to : ReportStatus.values()) {
            assertThat(ReportStatusTransitionPolicy.isAllowed(ReportStatus.FAILED, to)).isFalse();
            assertThat(ReportStatusTransitionPolicy.isAllowed(ReportStatus.CANCELLED, to)).isFalse();
            assertThat(ReportStatusTransitionPolicy.isAllowed(ReportStatus.EXPIRED, to)).isFalse();
        }
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
    void allEightStatusesExist() {
        assertThat(ReportStatus.values()).hasSize(8);
    }
}
