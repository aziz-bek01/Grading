package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.infrastructure.RoleJpaEntity;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.tenancy.application.TenantContext;

/**
 * Shared CUSTOM-role tenant-ownership guard reused by every manager of a role's
 * permissions or lifecycle (slice E2 {@link RolePermissionAdminUseCase} read +
 * write paths, slice E3 {@link CustomRoleUseCase}).
 *
 * <p>Extracted so the tenant-ownership rule lives in EXACTLY ONE place. Before
 * this guard, E2's {@code requireRole} resolved a role by id alone (tenant-
 * agnostic): for a CUSTOM role ({@code is_system = false}) there was no
 * {@code tenant_id == ctx.tenantId()} check, so a tenant admin of tenant A could
 * GET (disclose) or PUT (alter) the permissions of tenant B's custom role by id
 * (a Broken-Object-Level-Authorization hole). This guard closes that, reusing the
 * SAME ownership decision E3 already relied on via {@code findByIdAndTenantId}.
 *
 * <h3>Ownership rule (custom roles only)</h3>
 * <ul>
 *   <li><b>System role</b> ({@code is_system = true}, {@code tenant_id = NULL}) —
 *       NOT this guard's concern. System-role access is gated separately
 *       (super-admin only, see {@code canEdit}); this guard returns without
 *       throwing so the caller's own system-role gate applies.</li>
 *   <li><b>Custom role owned by the active tenant</b> — permitted.</li>
 *   <li><b>Custom role owned by ANOTHER tenant</b> — denied with
 *       {@link TenantAccessDeniedException} (→ 404, no existence reveal),
 *       UNLESS the caller is HRLab Super Admin (holds
 *       {@code USER_ROLE_ASSIGN_HRLAB}), who may act cross-tenant — consistent
 *       with the rest of the access module.</li>
 * </ul>
 *
 * <p>Stateless and read-only: no DB access (it operates on an already-loaded
 * {@link RoleJpaEntity}), no writes, no audit — the caller owns those.
 */
@Component
public class RoleOwnershipGuard {

    /**
     * Require that the caller may act on {@code role} given tenant ownership.
     * No-op for a system role (its access is gated elsewhere). For a custom role,
     * throws {@link TenantAccessDeniedException} (→ 404) unless the role belongs to
     * the active tenant or the caller is HRLab Super Admin.
     */
    public void requireCanManage(TenantContext ctx, RoleJpaEntity role) {
        if (!ownsOrSuperAdmin(ctx, role)) {
            throw new TenantAccessDeniedException(); // → 404, no cross-tenant reveal
        }
    }

    /**
     * {@code true} when the caller may act on {@code role}: a system role (gated
     * elsewhere) passes through; a custom role passes only when owned by the active
     * tenant or the caller is HRLab Super Admin.
     */
    public boolean ownsOrSuperAdmin(TenantContext ctx, RoleJpaEntity role) {
        if (role == null) {
            return false;
        }
        if (role.isSystem()) {
            // System roles carry no tenant; ownership does not apply here.
            return true;
        }
        if (ctx != null && ctx.hasPermission(PermissionCodes.USER_ROLE_ASSIGN_HRLAB)) {
            return true; // HRLab Super Admin may act cross-tenant.
        }
        return ctx != null
                && role.getTenantId() != null
                && role.getTenantId().equals(ctx.tenantId());
    }
}
