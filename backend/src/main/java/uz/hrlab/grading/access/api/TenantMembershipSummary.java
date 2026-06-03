package uz.hrlab.grading.access.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Lightweight tenant card surfaced by {@code GET /api/v1/users/me} —
 * one entry per {@code user_tenant_memberships} row the caller has, irrespective
 * of which one is currently active (frontend uses this list for the tenant
 * switcher).
 *
 * <p>Fields mirror the frontend {@code TenantSummary} contract
 * (see {@code frontend/src/shared/auth/authTypes.ts}). Brand attributes are
 * sourced from {@code client_companies} — when no client_company row exists
 * for a tenant (legacy or under-construction tenants) the brand name falls
 * back to {@code tenants.display_name} and {@code fingerprintHue} is
 * computed deterministically from the tenant slug.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantMembershipSummary(
        UUID id,
        String slug,
        String brandName,
        Integer fingerprintHue,
        String membershipStatus,
        boolean salaryDataPermission
) {
}
