package uz.hrlab.grading.tenancy.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;

import java.util.Set;
import java.util.UUID;

/**
 * ABAC policy for the Tenant + Client Company control-plane module
 * (Sprint F, E9 — F-1 BE).
 *
 * <p>Combines RBAC permission codes ({@code TENANT_*} / {@code CLIENT_*})
 * with two attribute axes:
 * <ol>
 *   <li><b>tenant membership</b> — to read/view a tenant or its client
 *       company, the caller must hold an ACTIVE membership in that tenant
 *       (cached on {@link TenantContext#tenantMemberships()}).
 *       HRLAB_SUPER_ADMIN bypasses this check (platform scope, architecture
 *       §8.4).</li>
 *   <li><b>mutation scope</b> — to update a tenant or client company, the
 *       caller's <b>active</b> tenant (security context, NOT the URL) must
 *       equal the target tenant id. This defeats the case where
 *       CLIENT_COMPANY_ADMIN holds multiple memberships (during HRLab
 *       onboarding migrations) and tries to flip a row in a tenant they
 *       happen to also belong to but are not currently acting in.</li>
 * </ol>
 *
 * <p>All denials raise {@link TenantAccessDeniedException} (mapped to 404)
 * to defeat cross-tenant probing — even though admin namespace surfaces
 * {@code tenantId} in the URL, we still refuse to confirm existence of a
 * tenant the caller has no membership in.
 *
 * <p>This policy is stateless and safe to share across threads.
 */
@Component
public class TenantManagementPolicy {

    // -------------------------------------------------------------- LIST

    /**
     * Cross-tenant listing (no scope filter) is allowed ONLY for
     * HRLAB_SUPER_ADMIN. Every other role gets a list scoped to their own
     * membership set (see {@link #scopedTenantIds(TenantContext)}).
     */
    public boolean canListAcrossTenants(TenantContext ctx) {
        return isSuperAdmin(ctx);
    }

    /**
     * Returns the set of tenant ids a non-super-admin caller is allowed to
     * see in a list query. Super Admin callers must use the unscoped query
     * instead — calling this for them is a programmer error.
     */
    public Set<UUID> scopedTenantIds(TenantContext ctx) {
        if (ctx == null) {
            throw new TenantAccessDeniedException();
        }
        Set<UUID> memberships = ctx.tenantMemberships();
        if (memberships == null || memberships.isEmpty()) {
            // Caller has no memberships -> nothing visible. Returning an empty
            // set lets the query return an empty page rather than 403, which
            // matches the pattern UserManagementPolicy uses for non-overlap.
            return Set.of();
        }
        return memberships;
    }

    // -------------------------------------------------------------- VIEW

    /**
     * Viewing a single tenant or its client_company is allowed when the
     * caller is a member of that tenant (or is Super Admin).
     */
    public void requireCanViewTenant(TenantContext ctx, UUID targetTenantId) {
        if (targetTenantId == null) {
            throw new TenantAccessDeniedException();
        }
        if (isSuperAdmin(ctx)) return;
        if (!isMemberOf(ctx, targetTenantId)) {
            // 404 via TenantAccessDenied — never reveal existence.
            throw new TenantAccessDeniedException();
        }
    }

    // ----------------------------------------------------------- MUTATE

    /**
     * Tenant-level mutation (PATCH display name / locale, archive). Super
     * Admin OR caller whose ACTIVE tenant equals the target. Non-super-admin
     * Client Company Admin can only edit their own active tenant — they
     * cannot flip a row in another tenant even if they happen to be a member.
     */
    public void requireCanManageTenant(TenantContext ctx, UUID targetTenantId) {
        if (targetTenantId == null) {
            throw new TenantAccessDeniedException();
        }
        if (isSuperAdmin(ctx)) return;
        if (!isMemberOf(ctx, targetTenantId)) {
            throw new TenantAccessDeniedException();
        }
        if (ctx.tenantId() == null || !ctx.tenantId().equals(targetTenantId)) {
            // Defense in depth: active tenant must match the URL.
            throw new TenantAccessDeniedException();
        }
    }

    /**
     * Client-company mutation (PATCH legalName, brandName, industry,
     * countryCode, taxId). Same rule as tenant mutation — but kept as a
     * separate method so future divergence (e.g. CLIENT_HR_DIRECTOR being
     * allowed to update brand_name but not legal_name) has an obvious home.
     */
    public void requireCanManageClientCompany(TenantContext ctx, UUID targetTenantId) {
        requireCanManageTenant(ctx, targetTenantId);
    }

    // -------------------------------------------------------------- HELP

    private static boolean isSuperAdmin(TenantContext ctx) {
        return ctx != null && ctx.hasRole(RoleCodes.HRLAB_SUPER_ADMIN);
    }

    private static boolean isMemberOf(TenantContext ctx, UUID tenantId) {
        if (ctx == null || tenantId == null) return false;
        Set<UUID> cache = ctx.tenantMemberships();
        if (cache != null) {
            return cache.contains(tenantId);
        }
        // Fallback for unit tests with a minimal context: trust ctx.tenantId().
        return tenantId.equals(ctx.tenantId());
    }
}
