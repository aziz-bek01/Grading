package uz.hrlab.grading.tenancy.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response for {@code GET /api/v1/admin/tenants/{tenantId}}.
 *
 * <p>Embeds the 1:1 {@code client_companies} row directly so the frontend
 * does not need a second round-trip. Stats are NOT embedded — they live on
 * {@code /{tenantId}/stats} which can be cached more aggressively.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantDetailResponse(
        UUID id,
        String slug,
        String displayName,
        String status,
        String isolationMode,
        String schemaName,
        String defaultLocale,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        ClientCompanySummary clientCompany
) {

    /** Embedded 1:1 client_companies projection — minimal surface, not the full DTO. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClientCompanySummary(
            UUID id,
            String legalName,
            String brandName,
            String industry,
            String countryCode,
            String taxId
    ) {
    }
}
