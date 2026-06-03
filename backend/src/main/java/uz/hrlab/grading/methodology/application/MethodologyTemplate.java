package uz.hrlab.grading.methodology.application;

import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.ScoringMode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Methodology template descriptor — seed data for the
 * "instantiate from template" use case. Implemented as in-memory registry to
 * keep Phase 4 MVP-light; the table {@code public.methodology_templates}
 * referenced in the architecture is reserved for MVP 2.
 */
public record MethodologyTemplate(
        String code,
        MethodologyType methodologyType,
        ScoringMode scoringMode,
        BigDecimal targetTotalPoints,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n,
        List<FactorTemplate> factors
) {

    public record FactorTemplate(
            String code,
            Map<String, String> nameI18n,
            BigDecimal weight,
            BigDecimal maxPoints,
            int sortOrder,
            boolean required,
            List<LevelTemplate> levels
    ) { }

    public record LevelTemplate(
            String code,
            int levelOrder,
            BigDecimal points,
            BigDecimal scaleValue,
            Map<String, String> labelI18n
    ) { }
}
