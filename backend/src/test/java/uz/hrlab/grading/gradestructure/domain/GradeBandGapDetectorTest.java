package uz.hrlab.grading.gradestructure.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("workflow")
class GradeBandGapDetectorTest {

    private final GradeBandGapDetector detector = new GradeBandGapDetector();

    private static GradeBand band(String min, String max) {
        return new GradeBand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal(min), new BigDecimal(max));
    }

    @Test
    void contiguousBandsHaveNoGap() {
        List<GradeBand> bands = List.of(
                band("0", "99.9999"),
                band("100.0000", "199.9999"),
                band("200.0000", "300.0000")
        );
        assertThat(detector.findGaps(bands)).isEmpty();
    }

    @Test
    void gapBetweenBandsIsDetected() {
        List<GradeBand> bands = List.of(
                band("0", "100"),
                band("200", "300")
        );
        var gaps = detector.findGaps(bands);
        assertThat(gaps).hasSize(1);
        assertThat(gaps.get(0).gapMin()).isEqualByComparingTo("100.0001");
        assertThat(gaps.get(0).gapMax()).isEqualByComparingTo("199.9999");
    }

    @Test
    void multipleGapsDetected() {
        List<GradeBand> bands = List.of(
                band("0", "100"),
                band("200", "300"),
                band("500", "600")
        );
        assertThat(detector.findGaps(bands)).hasSize(2);
    }

    @Test
    void singleBandHasNoGap() {
        assertThat(detector.findGaps(List.of(band("0", "100")))).isEmpty();
    }
}
