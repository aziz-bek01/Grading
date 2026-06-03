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
    void failedAndCancelledAndExpiredAreTerminal() {
        for (ExportJobStatus to : ExportJobStatus.values()) {
            assertThat(ExportJobStatusTransitionPolicy.isAllowed(ExportJobStatus.FAILED, to)).isFalse();
            assertThat(ExportJobStatusTransitionPolicy.isAllowed(ExportJobStatus.CANCELLED, to)).isFalse();
            assertThat(ExportJobStatusTransitionPolicy.isAllowed(ExportJobStatus.EXPIRED, to)).isFalse();
        }
    }

    @Test
    void illegalTransitionThrows() {
        assertThatThrownBy(() -> ExportJobStatusTransitionPolicy.assertAllowed(
                ExportJobStatus.REQUESTED, ExportJobStatus.DOWNLOADED))
                .isInstanceOf(ExportJobTransitionRejectedException.class);
    }

    @Test
    void allEightStatusesExist() {
        assertThat(ExportJobStatus.values()).hasSize(8);
    }
}
