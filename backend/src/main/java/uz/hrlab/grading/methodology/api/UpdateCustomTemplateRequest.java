package uz.hrlab.grading.methodology.api;

import uz.hrlab.grading.common.validation.SupportedLocaleKeys;

import java.util.Map;

/**
 * Rename a tenant CUSTOM template (Epic E) — name/description i18n only. The
 * code and frozen factor snapshot are immutable; save a new template to change
 * the skeleton. Never carries tenantId (security-blueprint §13).
 */
public record UpdateCustomTemplateRequest(
        @SupportedLocaleKeys Map<String, String> nameI18n,
        @SupportedLocaleKeys Map<String, String> descriptionI18n
) { }
