package uz.hrlab.grading.gradestructure.application;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("workflow")
class GradeStructureTemplateRegistryTest {

    private final GradeStructureTemplateRegistry registry = new GradeStructureTemplateRegistry();

    @Test
    void grade14TemplateHas14Grades() {
        GradeStructureTemplate t = registry.require("GRADE_14");
        assertThat(t.grades()).hasSize(14);
        assertThat(t.grades().get(0).gradeNumber()).isEqualTo(1);
        assertThat(t.grades().get(13).gradeNumber()).isEqualTo(14);
    }

    @Test
    void grade16TemplateHas16Grades() {
        GradeStructureTemplate t = registry.require("GRADE_16");
        assertThat(t.grades()).hasSize(16);
    }

    @Test
    void customTemplateIsEmpty() {
        GradeStructureTemplate t = registry.require("CUSTOM");
        assertThat(t.grades()).isEmpty();
    }

    @Test
    void grade14BandsCoverZeroToThousand() {
        GradeStructureTemplate t = registry.require("GRADE_14");
        assertThat(t.grades().get(0).minScore()).isEqualByComparingTo("0");
        assertThat(t.grades().get(13).maxScore()).isEqualByComparingTo(new BigDecimal("1000"));
    }

    @Test
    void grade14BandsAreContiguous() {
        GradeStructureTemplate t = registry.require("GRADE_14");
        BigDecimal epsilon = new BigDecimal("0.0001");
        for (int i = 1; i < t.grades().size(); i++) {
            BigDecimal prevMax = t.grades().get(i - 1).maxScore();
            BigDecimal currMin = t.grades().get(i).minScore();
            BigDecimal diff = currMin.subtract(prevMax);
            // diff should equal epsilon (contiguous) — strictly positive, ≤ 2 * epsilon
            assertThat(diff).isEqualByComparingTo(epsilon);
        }
    }

    @Test
    void grade14NamesContainAllFourLocales() {
        GradeStructureTemplate t = registry.require("GRADE_14");
        var first = t.grades().get(0).nameI18n();
        assertThat(first).containsKeys("ru-RU", "en-US", "uz-Cyrl-UZ", "uz-Latn-UZ");
    }
}
