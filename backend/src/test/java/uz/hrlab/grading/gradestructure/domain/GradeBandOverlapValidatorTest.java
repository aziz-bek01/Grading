package uz.hrlab.grading.gradestructure.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("workflow")
class GradeBandOverlapValidatorTest {

    private final GradeBandOverlapValidator validator = new GradeBandOverlapValidator();

    private static GradeBand band(String min, String max) {
        return new GradeBand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal(min), new BigDecimal(max));
    }

    @Test
    void emptyListHasNoOverlaps() {
        assertThat(validator.findOverlaps(List.of())).isEmpty();
    }

    @Test
    void singleBandHasNoOverlaps() {
        assertThat(validator.findOverlaps(List.of(band("0", "100")))).isEmpty();
    }

    @Test
    void nonOverlappingBandsPass() {
        List<GradeBand> bands = List.of(
                band("0", "99.9999"),
                band("100", "199.9999"),
                band("200", "300")
        );
        assertThat(validator.findOverlaps(bands)).isEmpty();
    }

    @Test
    void touchingBoundariesAreOverlap() {
        // G1: 0-100, G2: 100-200 — point 100 belongs to both → overlap by convention.
        List<GradeBand> bands = List.of(
                band("0", "100"),
                band("100", "200")
        );
        var conflicts = validator.findOverlaps(bands);
        assertThat(conflicts).hasSize(1);
    }

    @Test
    void overlappingBandsAreDetected() {
        List<GradeBand> bands = List.of(
                band("0", "150"),
                band("100", "200")
        );
        var conflicts = validator.findOverlaps(bands);
        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).intersectionMin()).isEqualByComparingTo("100");
        assertThat(conflicts.get(0).intersectionMax()).isEqualByComparingTo("150");
    }

    @Test
    void fullyNestedBandsAreOverlap() {
        List<GradeBand> bands = List.of(
                band("0", "1000"),
                band("100", "200")
        );
        assertThat(validator.findOverlaps(bands)).hasSize(1);
    }

    @Test
    void identicalBandsAreOverlap() {
        List<GradeBand> bands = List.of(
                band("0", "100"),
                band("0", "100")
        );
        assertThat(validator.findOverlaps(bands)).hasSize(1);
    }

    @Test
    void multipleOverlapsReturnAllConflicts() {
        List<GradeBand> bands = List.of(
                band("0", "150"),
                band("100", "200"),
                band("180", "250")
        );
        var conflicts = validator.findOverlaps(bands);
        // (0-150 vs 100-200), (100-200 vs 180-250) → 2 pairs
        assertThat(conflicts).hasSize(2);
    }

    @Test
    void overlapsCheckIsSymmetric() {
        GradeBand a = band("0", "100");
        GradeBand b = band("50", "150");
        assertThat(validator.overlaps(a, b)).isTrue();
        assertThat(validator.overlaps(b, a)).isTrue();
    }

    @Test
    void contiguousBandsWithEpsilonGapDoNotOverlap() {
        List<GradeBand> bands = List.of(
                band("0", "99.9999"),
                band("100.0000", "199.9999")
        );
        assertThat(validator.findOverlaps(bands)).isEmpty();
    }
}
