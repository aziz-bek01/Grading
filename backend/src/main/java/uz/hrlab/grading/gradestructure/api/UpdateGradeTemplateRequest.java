package uz.hrlab.grading.gradestructure.api;

import uz.hrlab.grading.common.validation.SupportedLocaleKeys;

import java.util.Map;

/**
 * Rename a tenant CUSTOM grade template (BE-9) — name/description i18n only. The
 * code and frozen grade snapshot are immutable; save a new template to change the
 * skeleton. Never carries tenantId (security-blueprint §13). Mirrors the
 * methodology {@code UpdateCustomTemplateRequest}.
 */
public record UpdateGradeTemplateRequest(
        @SupportedLocaleKeys Map<String, String> nameI18n,
        @SupportedLocaleKeys Map<String, String> descriptionI18n
) { }
