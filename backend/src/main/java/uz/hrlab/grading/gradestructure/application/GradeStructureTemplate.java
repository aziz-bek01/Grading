package uz.hrlab.grading.gradestructure.application;

import uz.hrlab.grading.gradestructure.domain.GradeStructureType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * In-memory template descriptor for the "instantiate from template" use case.
 * Mirrors the {@code MethodologyTemplate} pattern.
 */
public record GradeStructureTemplate(
        String code,
        GradeStructureType structureType,
        int gradeCount,
        BigDecimal totalRangeMin,
        BigDecimal totalRangeMax,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n,
        List<GradeRowTemplate> grades
) {

    public record GradeRowTemplate(
            int gradeNumber,
            int sortOrder,
            Map<String, String> nameI18n,
            BigDecimal minScore,
            BigDecimal maxScore
    ) { }
}
