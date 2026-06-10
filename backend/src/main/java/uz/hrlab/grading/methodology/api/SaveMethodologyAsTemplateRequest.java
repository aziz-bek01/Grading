package uz.hrlab.grading.methodology.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import uz.hrlab.grading.common.validation.SupportedLocaleKeys;

import java.util.Map;

/**
 * Save a methodology's LATEST version as a reusable tenant CUSTOM template
 * (Epic E) — body for {@code POST /api/v1/methodologies/{id}/save-as-template}.
 * The methodology id comes from the path; the source version is its latest. The
 * tenant is NEVER carried here (security-blueprint §13).
 */
public record SaveMethodologyAsTemplateRequest(
        @NotBlank String code,
        @NotEmpty @SupportedLocaleKeys Map<String, String> nameI18n,
        @SupportedLocaleKeys Map<String, String> descriptionI18n
) { }
