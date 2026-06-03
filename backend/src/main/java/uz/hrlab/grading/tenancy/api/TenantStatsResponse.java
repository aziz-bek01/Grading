package uz.hrlab.grading.tenancy.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response for {@code GET /api/v1/admin/tenants/{tenantId}/stats}.
 * Aggregated counters — cheap to compute, safe to cache for ~60s.
 *
 * <p>Counts are computed against the control-plane view of the tenant:
 * {@code projects} (tenant-scoped business data), {@code user_tenant_memberships}
 * (control-plane). {@code lastActivityAt} is the {@code created_at} of the
 * latest {@code system_audit_log} row for the tenant — used as a UI signal
 * of "live" vs "dormant" tenants.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantStatsResponse(
        UUID tenantId,
        long projectCount,
        long userCount,
        OffsetDateTime lastActivityAt
) {
}
