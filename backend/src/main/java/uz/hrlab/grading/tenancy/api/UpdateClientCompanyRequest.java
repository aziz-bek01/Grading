package uz.hrlab.grading.tenancy.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH body for {@code PATCH /api/v1/admin/clients/{clientId}}.
 * All fields optional; null = no change.
 *
 * <p>Note: never accepts {@code tenantId} (security-blueprint §13 — the
 * 1:1 link is immutable; rebinding a client_company to a different tenant
 * is a tenant-migration operation handled separately).
 */
public record UpdateClientCompanyRequest(
        @Size(min = 1, max = 500)
        String legalName,

        @Size(max = 255)
        String brandName,

        @Size(max = 100)
        String industry,

        @Pattern(regexp = "[A-Z]{2}",
                message = "countryCode must be a 2-letter uppercase ISO 3166-1 alpha-2")
        String countryCode,

        @Size(max = 50)
        String taxId
) {
}
