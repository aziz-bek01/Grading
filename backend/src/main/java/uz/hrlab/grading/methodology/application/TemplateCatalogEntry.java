package uz.hrlab.grading.methodology.application;

import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.ScoringMode;

import java.util.Map;
import java.util.UUID;

/**
 * One row of the unified create-from-template catalog (Epic E) — the UNION of
 * built-in registry skeletons ({@link TemplateSource#BUILTIN}) and the tenant's
 * ACTIVE DB-backed custom templates ({@link TemplateSource#CUSTOM}).
 *
 * <p>{@code id} is {@code null} for built-ins (they have no DB row; they are
 * selected by {@link #code}); it carries the {@code methodology_templates.id}
 * for custom rows so the FE can target the manage (rename/archive) endpoints.
 * {@code factorCount} + {@code defaultScoringMode} are derived so the picker can
 * render without instantiating.
 */
public record TemplateCatalogEntry(
        UUID id,
        String code,
        TemplateSource source,
        boolean builtin,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n,
        MethodologyType methodologyType,
        ScoringMode defaultScoringMode,
        int factorCount
) { }
