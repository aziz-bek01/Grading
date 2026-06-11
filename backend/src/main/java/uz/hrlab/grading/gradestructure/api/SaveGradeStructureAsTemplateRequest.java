package uz.hrlab.grading.gradestructure.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import uz.hrlab.grading.common.validation.SupportedLocaleKeys;

import java.util.Map;

/**
 * Save a grade structure as a reusable tenant CUSTOM template (BE-9). The tenant
 * is NEVER carried here — it is resolved from {@code TenantContext}
 * (security-blueprint §13; ArchitectureTest Rule 7). Mirrors the methodology
 * {@code SaveMethodologyAsTemplateRequest}.
 */
public record SaveGradeStructureAsTemplateRequest(
        @NotBlank String code,
        @NotEmpty @SupportedLocaleKeys Map<String, String> nameI18n,
        @SupportedLocaleKeys Map<String, String> descriptionI18n
) { }
