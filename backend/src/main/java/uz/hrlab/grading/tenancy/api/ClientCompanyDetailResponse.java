package uz.hrlab.grading.tenancy.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/** Response for {@code GET /api/v1/admin/clients/{clientId}}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientCompanyDetailResponse(
        UUID id,
        UUID tenantId,
        String tenantSlug,
        String tenantDisplayName,
        String legalName,
        String brandName,
        String industry,
        String countryCode,
        String taxId
) {
}
