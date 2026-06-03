package uz.hrlab.grading.tenancy.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row of {@code GET /api/v1/admin/tenants} — flat tenant + brand
 * projection enriched with the two portfolio KPIs the admin list table
 * renders ({@code projectCount} / {@code userCount}). Heavier per-tenant
 * stats (last activity) still live behind {@code GET /{id}/stats}; the two
 * counts here are O(1) indexed look-ups reused from
 * {@code GetTenantStatsQuery} so the list view does not need an extra
 * round-trip per row. No encryption / storage internals are exposed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantListResponse(
        UUID id,
        String slug,
        String displayName,
        String status,
        String isolationMode,
        String defaultLocale,
        String clientLegalName,
        String clientBrandName,
        String clientIndustry,
        OffsetDateTime createdAt,
        long projectCount,
        long userCount
) {
}
