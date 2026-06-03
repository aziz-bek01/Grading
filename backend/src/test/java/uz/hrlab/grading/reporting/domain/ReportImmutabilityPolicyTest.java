package uz.hrlab.grading.reporting.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("workflow")
class ReportImmutabilityPolicyTest {

    @Test
    void generatedDownloadedExpiredAreImmutable() {
        assertThat(ReportImmutabilityPolicy.isImmutable(ReportStatus.GENERATED)).isTrue();
        assertThat(ReportImmutabilityPolicy.isImmutable(ReportStatus.DOWNLOADED)).isTrue();
        assertThat(ReportImmutabilityPolicy.isImmutable(ReportStatus.EXPIRED)).isTrue();
    }

    @Test
    void inFlightStatesAreMutable() {
        assertThat(ReportImmutabilityPolicy.isImmutable(ReportStatus.REQUESTED)).isFalse();
        assertThat(ReportImmutabilityPolicy.isImmutable(ReportStatus.QUEUED)).isFalse();
        assertThat(ReportImmutabilityPolicy.isImmutable(ReportStatus.GENERATING)).isFalse();
    }

    @Test
    void failedCanBeRetriedByCloning() {
        assertThat(ReportImmutabilityPolicy.canRetryByCloning(ReportStatus.FAILED)).isTrue();
        assertThat(ReportImmutabilityPolicy.canRetryByCloning(ReportStatus.GENERATED)).isFalse();
        assertThat(ReportImmutabilityPolicy.canRetryByCloning(ReportStatus.CANCELLED)).isFalse();
    }
}
