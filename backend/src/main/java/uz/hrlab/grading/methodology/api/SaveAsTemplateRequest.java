package uz.hrlab.grading.methodology.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import uz.hrlab.grading.common.validation.SupportedLocaleKeys;

import java.util.Map;
import java.util.UUID;

/**
 * Save a methodology version as a reusable tenant CUSTOM template (Epic E).
 *
 * <p>{@code sourceVersionId} is the methodology VERSION to snapshot. The tenant
 * is NEVER carried here — it is resolved from {@code TenantContext}
 * (security-blueprint §13; ArchitectureTest Rule 7).
 */
public record SaveAsTemplateRequest(
        @NotNull UUID sourceVersionId,
        @NotBlank String code,
        @NotEmpty @SupportedLocaleKeys Map<String, String> nameI18n,
        @SupportedLocaleKeys Map<String, String> descriptionI18n
) { }
