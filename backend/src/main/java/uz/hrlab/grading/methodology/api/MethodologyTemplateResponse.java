package uz.hrlab.grading.methodology.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import uz.hrlab.grading.methodology.application.TemplateCatalogEntry;
import uz.hrlab.grading.methodology.domain.ScoringMode;

import java.util.Map;
import java.util.UUID;

/**
 * One row of the unified create-from-template picker catalog. Mirrors the
 * frontend {@code MethodologyTemplate} type
 * ({@code methodologyApi.fetchMethodologyTemplates}); field names serialize to
 * snake_case via the global Jackson strategy ({@code id, code, source,
 * is_builtin, name_i18n, description_i18n, factor_count, default_scoring_mode}).
 *
 * <p>Epic E: the catalog is now the UNION of global built-ins
 * ({@code is_builtin=true}, {@code source=BUILTIN}, {@code id=null} — selected by
 * code) and the tenant's ACTIVE DB-backed custom templates
 * ({@code is_builtin=false}, {@code source=CUSTOM}, {@code id} = the
 * {@code methodology_templates} row id used to target rename/archive). Cross-
 * tenant custom templates are never present (tenant-scoped query + RLS).
 */
public record MethodologyTemplateResponse(
        UUID id,
        String code,
        String source,
        // Explicit key so the FE picker contract is stable as `is_builtin`
        // regardless of the global PropertyNamingStrategy's handling of an
        // `isX` boolean record component.
        @JsonProperty("is_builtin") boolean builtin,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n,
        int factorCount,
        ScoringMode defaultScoringMode
) {
    public static MethodologyTemplateResponse from(TemplateCatalogEntry e) {
        return new MethodologyTemplateResponse(
                e.id(),
                e.code(),
                e.source() == null ? null : e.source().name(),
                e.builtin(),
                e.nameI18n(),
                e.descriptionI18n(),
                e.factorCount(),
                e.defaultScoringMode());
    }
}
