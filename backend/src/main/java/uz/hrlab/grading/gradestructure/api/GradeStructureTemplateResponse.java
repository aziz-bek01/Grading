package uz.hrlab.grading.gradestructure.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import uz.hrlab.grading.gradestructure.application.GradeTemplateCatalogEntry;
import uz.hrlab.grading.gradestructure.domain.GradeStructureType;

import java.util.Map;
import java.util.UUID;

/**
 * One row of the unified create-from-template picker catalog (BE-9). Mirrors the
 * methodology {@code MethodologyTemplateResponse}; field names serialize to
 * snake_case via the global Jackson strategy ({@code id, code, source,
 * is_builtin, name_i18n, description_i18n, structure_type, grade_count}).
 *
 * <p>The catalog is the UNION of global built-ins ({@code is_builtin=true},
 * {@code source=BUILTIN}, {@code id=null} — selected by code) and the tenant's
 * ACTIVE DB-backed custom templates ({@code is_builtin=false},
 * {@code source=CUSTOM}, {@code id} = the {@code grade_templates} row id used to
 * target rename/archive). Cross-tenant custom templates are never present
 * (tenant-scoped query + RLS).
 */
public record GradeStructureTemplateResponse(
        UUID id,
        String code,
        String source,
        // Explicit key so the FE picker contract is stable as `is_builtin`.
        @JsonProperty("is_builtin") boolean builtin,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n,
        GradeStructureType structureType,
        int gradeCount
) {
    public static GradeStructureTemplateResponse from(GradeTemplateCatalogEntry e) {
        return new GradeStructureTemplateResponse(
                e.id(),
                e.code(),
                e.source() == null ? null : e.source().name(),
                e.builtin(),
                e.nameI18n(),
                e.descriptionI18n(),
                e.structureType(),
                e.gradeCount());
    }
}
