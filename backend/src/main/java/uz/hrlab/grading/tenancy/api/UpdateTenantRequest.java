package uz.hrlab.grading.tenancy.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH body for {@code PATCH /api/v1/admin/tenants/{tenantId}}.
 *
 * <p>All fields are optional — caller sends only the keys to mutate. The
 * use case skips silently when a key is null. Status flips are restricted
 * (ACTIVE / SUSPENDED only) — archive is performed via the dedicated
 * {@code POST /{tenantId}/archive} endpoint so audits use a distinct action
 * code.
 *
 * <p>Slug + isolationMode are immutable once provisioned (database-blueprint
 * §5.1 — schema_name is derived from slug, changing it would break tenant
 * data routing).
 */
public record UpdateTenantRequest(
        @Size(min = 1, max = 255)
        String displayName,

        @Pattern(regexp = "ru-RU|uz-Cyrl-UZ|uz-Latn-UZ|en-US",
                message = "defaultLocale must be one of ru-RU|uz-Cyrl-UZ|uz-Latn-UZ|en-US")
        String defaultLocale,

        @Pattern(regexp = "ACTIVE|SUSPENDED",
                message = "status flip via PATCH may only be ACTIVE or SUSPENDED — " +
                        "use POST /{id}/archive for ARCHIVED")
        String status
) {
}
