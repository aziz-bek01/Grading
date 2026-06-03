package uz.hrlab.grading.methodology.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("workflow")
class MethodologyWeightValidationPolicyTest {

    private final MethodologyWeightValidationPolicy policy =
            new MethodologyWeightValidationPolicy();

    @Test
    void directPointsDoesNotCheckWeightSum() {
        assertThatCode(() -> policy.validate(
                ScoringMode.DIRECT_POINTS,
                new BigDecimal("1000"),
                List.of(weight("10"), weight("20"))))
                .doesNotThrowAnyException();
    }

    @Test
    void weightedPointsAcceptsExactHundred() {
        assertThatCode(() -> policy.validate(
                ScoringMode.WEIGHTED_POINTS,
                null,
                List.of(weight("40"), weight("60"))))
                .doesNotThrowAnyException();
    }

    @Test
    void weightedPointsRejectsBelowHundred() {
        assertThatThrownBy(() -> policy.validate(
                ScoringMode.WEIGHTED_POINTS,
                null,
                List.of(weight("49.99"), weight("50.00"))))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class)
                .hasMessageContaining("100.0000");
    }

    @Test
    void weightedPointsRejectsAboveHundred() {
        assertThatThrownBy(() -> policy.validate(
                ScoringMode.WEIGHTED_POINTS,
                null,
                List.of(weight("50.01"), weight("50.00"))))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
    }

    @Test
    void weightedScaleRejectsWhenTargetTotalPointsMissing() {
        assertThatThrownBy(() -> policy.validate(
                ScoringMode.WEIGHTED_SCALE,
                null,
                List.of(weight("500"))))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class)
                .hasMessageContaining("targetTotalPoints");
    }

    @Test
    void weightedScaleAcceptsExactTarget() {
        assertThatCode(() -> policy.validate(
                ScoringMode.WEIGHTED_SCALE,
                new BigDecimal("1000.0000"),
                List.of(weight("400"), weight("600"))))
                .doesNotThrowAnyException();
    }

    @Test
    void weightedScaleRejectsOffByOne() {
        assertThatThrownBy(() -> policy.validate(
                ScoringMode.WEIGHTED_SCALE,
                new BigDecimal("1000"),
                List.of(weight("400"), weight("599"))))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
    }

    @Test
    void emptyFactorsRejected() {
        assertThatThrownBy(() -> policy.validate(
                ScoringMode.DIRECT_POINTS,
                null,
                List.of()))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
    }

    // --- Boundary tests (D-411 / D-401) -----------------------------------

    /**
     * Boundary cases for the ±0.0001 tolerance contract on WEIGHTED_POINTS.
     * Each row is "single factor weight, expectPass" — using one factor lets
     * us drive the sum precisely.
     */
    @ParameterizedTest(name = "WEIGHTED_POINTS sum={0} → expectPass={1}")
    @CsvSource({
            // Inside tolerance — must pass
            "100.0000, true",
            "99.9999, true",
            "100.0001, true",
            // Outside tolerance — must reject
            "100.0002, false",
            "99.9998, false",
            "99.99,    false",
            "100.01,   false"
    })
    void weightedPointsBoundaryTolerance(String sumWeight, boolean expectPass) {
        var factors = List.of(weight(sumWeight));
        if (expectPass) {
            assertThatCode(() -> policy.validate(
                    ScoringMode.WEIGHTED_POINTS, null, factors))
                    .as("sum=%s should be accepted within ±0.0001", sumWeight)
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> policy.validate(
                    ScoringMode.WEIGHTED_POINTS, null, factors))
                    .as("sum=%s should be rejected outside ±0.0001", sumWeight)
                    .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
        }
    }

    @ParameterizedTest(name = "WEIGHTED_SCALE sum={0} target=1000.0000 → expectPass={1}")
    @CsvSource({
            "1000.0000, true",
            "999.9999,  true",
            "1000.0001, true",
            "1000.0002, false",
            "999.9998,  false"
    })
    void weightedScaleBoundaryTolerance(String sumWeight, boolean expectPass) {
        var factors = List.of(weight(sumWeight));
        BigDecimal target = new BigDecimal("1000.0000");
        if (expectPass) {
            assertThatCode(() -> policy.validate(
                    ScoringMode.WEIGHTED_SCALE, target, factors))
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> policy.validate(
                    ScoringMode.WEIGHTED_SCALE, target, factors))
                    .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
        }
    }

    @Test
    void toleranceConstantIsExposed() {
        // Frontend may import this value via OpenAPI / shared constants.
        // Pin the value so a silent change cannot diverge from the UI.
        org.assertj.core.api.Assertions.assertThat(
                        MethodologyWeightValidationPolicy.WEIGHT_SUM_TOLERANCE)
                .isEqualByComparingTo(new BigDecimal("0.0001"));
    }

    private static MethodologyWeightValidationPolicy.FactorWeightView weight(String v) {
        BigDecimal bd = new BigDecimal(v);
        return () -> bd;
    }
}
