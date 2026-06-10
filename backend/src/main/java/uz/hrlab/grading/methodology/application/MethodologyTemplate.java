package uz.hrlab.grading.methodology.application;

import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.ScoringMode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Methodology template descriptor — the COMMON instantiation shape consumed by
 * {@code CreateMethodologyFromTemplateUseCase}. Both the in-memory built-in
 * registry ({@code MethodologyTemplateRegistry}) and DB-backed tenant CUSTOM
 * templates ({@code methodology_templates}) resolve to this single shape via the
 * {@link TemplateCatalog} port, so the use case deep-copies through one path.
 *
 * <p>Epic E note: factor {@code descriptionI18n} and level
 * {@code descriptionI18n} were added so a custom template (which freezes those
 * fields in its {@code factors_snapshot}) round-trips loss-free. Built-ins pass
 * {@code null} for them.
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
            Map<String, String> descriptionI18n,
            BigDecimal weight,
            BigDecimal maxPoints,
            int sortOrder,
            boolean required,
            List<LevelTemplate> levels
    ) {
        /** Built-in convenience: factor with no description. */
        public FactorTemplate(String code, Map<String, String> nameI18n,
                              BigDecimal weight, BigDecimal maxPoints,
                              int sortOrder, boolean required, List<LevelTemplate> levels) {
            this(code, nameI18n, null, weight, maxPoints, sortOrder, required, levels);
        }
    }

    public record LevelTemplate(
            String code,
            int levelOrder,
            BigDecimal points,
            BigDecimal scaleValue,
            Map<String, String> labelI18n,
            Map<String, String> descriptionI18n
    ) {
        /** Built-in convenience: level with no description. */
        public LevelTemplate(String code, int levelOrder, BigDecimal points,
                             BigDecimal scaleValue, Map<String, String> labelI18n) {
            this(code, levelOrder, points, scaleValue, labelI18n, null);
        }
    }
}
