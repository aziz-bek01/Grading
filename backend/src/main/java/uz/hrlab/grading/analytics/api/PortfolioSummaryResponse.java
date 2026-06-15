package uz.hrlab.grading.analytics.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response for {@code GET /api/v1/analytics/portfolio-summary} — the dashboard
 * portfolio widget for the ACTIVE tenant. All counts are tenant-scoped (sourced
 * from {@link uz.hrlab.grading.tenancy.application.TenantContext}, never the
 * client). Aggregated, indexed counters — cheap to compute.
 *
 * <p>{@code clientCompanyCount} is the number of legal entities under the active
 * tenant (1 in the current single-company-per-tenant model, kept as a count so
 * the shape survives a future multi-company tenant). {@code lastActivityAt} is
 * the {@code created_at} of the latest {@code system_audit_log} row for the
 * tenant — null when the tenant has no audit rows yet (NON_NULL → omitted).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortfolioSummaryResponse(
        UUID tenantId,
        long clientCompanyCount,
        long projectCount,
        long methodologyCount,
        OffsetDateTime lastActivityAt
) {
}
