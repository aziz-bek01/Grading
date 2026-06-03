package uz.hrlab.grading.phase4;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.methodology.application.MethodologyTemplate;
import uz.hrlab.grading.methodology.application.MethodologyTemplateRegistry;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.ScoringMode;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("workflow")
class Phase4TemplateRegistryTest {

    private final MethodologyTemplateRegistry registry = new MethodologyTemplateRegistry();

    @Test
    void classic8FactorHas8FactorsAnd1000PointTarget() {
        MethodologyTemplate t = registry.require("CLASSIC_8_FACTOR");
        assertThat(t.methodologyType()).isEqualTo(MethodologyType.CLASSIC_8_FACTOR);
        assertThat(t.scoringMode()).isEqualTo(ScoringMode.DIRECT_POINTS);
        assertThat(t.targetTotalPoints()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(t.factors()).hasSize(8);
        BigDecimal sum = t.factors().stream()
                .map(MethodologyTemplate.FactorTemplate::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(new BigDecimal("1000"));
        t.factors().forEach(f -> assertThat(f.levels()).hasSize(5));
    }

    @Test
    void extended11CriteriaWeightsSumTo100() {
        MethodologyTemplate t = registry.require("EXTENDED_11_CRITERIA");
        assertThat(t.scoringMode()).isEqualTo(ScoringMode.WEIGHTED_POINTS);
        assertThat(t.factors()).hasSize(11);
        BigDecimal sum = t.factors().stream()
                .map(MethodologyTemplate.FactorTemplate::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void customIsEmpty() {
        MethodologyTemplate t = registry.require("CUSTOM");
        assertThat(t.methodologyType()).isEqualTo(MethodologyType.CUSTOM);
        assertThat(t.factors()).isEmpty();
    }

    @Test
    void unknownTemplateThrows404() {
        assertThatThrownBy(() -> registry.require("NOPE"))
                .hasMessageContaining("not found");
    }
}
