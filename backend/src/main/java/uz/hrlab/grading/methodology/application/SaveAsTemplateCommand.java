package uz.hrlab.grading.methodology.application;

import java.util.Map;
import java.util.UUID;

/**
 * Command for {@code SaveAsTemplateUseCase} (Epic E). Freezes the factors +
 * levels of a methodology version into a reusable tenant CUSTOM template.
 *
 * <p>{@code sourceVersionId} is the methodology VERSION whose factor/level
 * skeleton is snapshotted. {@code code} is the new per-tenant stable template
 * code (must be unique in the tenant and must not shadow a built-in). The
 * tenant is taken from {@code TenantContext} by the use case — never from a
 * request body.
 */
public record SaveAsTemplateCommand(
        UUID sourceVersionId,
        String code,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n
) { }
