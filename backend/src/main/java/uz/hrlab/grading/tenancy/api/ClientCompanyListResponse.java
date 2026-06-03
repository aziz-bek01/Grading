package uz.hrlab.grading.tenancy.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * One row of {@code GET /api/v1/admin/clients}. Each row carries the
 * parent tenant slug + display name so the frontend can render a table
 * without a second fetch.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientCompanyListResponse(
        UUID id,
        UUID tenantId,
        String tenantSlug,
        String tenantDisplayName,
        String tenantStatus,
        String legalName,
        String brandName,
        String industry,
        String countryCode
) {
}
