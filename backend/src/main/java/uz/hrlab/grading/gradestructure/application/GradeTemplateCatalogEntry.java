package uz.hrlab.grading.gradestructure.application;

import uz.hrlab.grading.gradestructure.domain.GradeStructureType;

import java.util.Map;
import java.util.UUID;

/**
 * One row of the unified create-from-template catalog (BE-8) — the UNION of
 * built-in registry skeletons ({@link GradeTemplateSource#BUILTIN}) and the
 * tenant's ACTIVE DB-backed custom templates ({@link GradeTemplateSource#CUSTOM}).
 * Mirrors the methodology {@code TemplateCatalogEntry}.
 *
 * <p>{@code id} is {@code null} for built-ins (they have no DB row; selected by
 * {@link #code}); it carries the {@code grade_templates.id} for custom rows so
 * the FE can target the manage (rename/archive) endpoints. {@code gradeCount} is
 * derived so the picker can render without instantiating.
 */
public record GradeTemplateCatalogEntry(
        UUID id,
        String code,
        GradeTemplateSource source,
        boolean builtin,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n,
        GradeStructureType structureType,
        int gradeCount
) { }
