package uz.hrlab.grading.methodology.api;

import uz.hrlab.grading.methodology.application.MethodologyTemplate;
import uz.hrlab.grading.methodology.domain.ScoringMode;

import java.util.Map;

/**
 * Read-only view of a built-in methodology template for the create-from-template
 * picker (slice B1). Mirrors the frontend {@code MethodologyTemplate} type
 * ({@code methodologyApi.fetchMethodologyTemplates}); field names serialize to
 * snake_case via the global Jackson strategy
 * ({@code code, name_i18n, description_i18n, factor_count, default_scoring_mode}).
 *
 * <p>{@code factorCount} and {@code defaultScoringMode} are DERIVED from the
 * in-memory {@link MethodologyTemplate} skeleton — no persistence, no new table
 * (DB-backed custom templates are a later slice E).
 */
public record MethodologyTemplateResponse(
        String code,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n,
        int factorCount,
        ScoringMode defaultScoringMode
) {
    public static MethodologyTemplateResponse from(MethodologyTemplate t) {
        return new MethodologyTemplateResponse(
                t.code(),
                t.nameI18n(),
                t.descriptionI18n(),
                t.factors() == null ? 0 : t.factors().size(),
                t.scoringMode());
    }
}
