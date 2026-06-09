package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.domain.RoleScope;
import uz.hrlab.grading.access.infrastructure.RoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ABAC policy for the User Management module (MVP 1 Phase 1.5).
 *
 * <p>Combines RBAC permission codes with two attribute axes:
 * <ol>
 *   <li><b>tenant overlap</b> — to read/list/view a user, the caller must share
 *       at least one ACTIVE/INVITED/SUSPENDED membership tenant with the
 *       target user. HRLab Super Admin bypasses this check (platform scope).</li>
 *   <li><b>role scope</b> — to assign an {@code HRLAB_*} role, the caller must
 *       hold {@code USER_ROLE_ASSIGN_HRLAB} (granted only to Super Admin).
 *       To toggle salary permission, the caller must NOT be flipping it on an
 *       HRLab membership (those callers must not read client salary —
 *       architecture §8.4) and must NOT be self-flipping their own row.</li>
 * </ol>
 *
 * <p>All denials raise {@link TenantAccessDeniedException} (mapped to 404 to
 * defeat cross-tenant probing) EXCEPT salary-permission denials raise
 * {@link PermissionDeniedException} (403) because the URL is already
 * tenant-bound by the path — leaking 403 here is acceptable and the frontend
 * needs the distinct status to render a permission-specific error message.
 *
 * <p>The policy is stateless and may be safely reused across threads.
 */
@Component
public class UserManagementPolicy {

    /** Membership statuses the caller must hold in {@code tenantId} to qualify. */
    private static final Set<String> ACTIVE_MEMBERSHIP_STATES =
            Set.of("ACTIVE", "INVITED", "SUSPENDED");

    private final UserTenantMembershipRepository memberships;

    public UserManagementPolicy(UserTenantMembershipRepository memberships) {
        this.memberships = memberships;
    }

    // -------------------------------------------------------------- LIST/VIEW

    /**
     * Cross-tenant list (no {@code tenantId} filter) is allowed ONLY for
     * HRLab Super Admin. Every other role is forced to scope the list to a
     * specific tenant they belong to.
     */
    public void requireCanListAcrossTenants(TenantContext ctx) {
        if (!isSuperAdmin(ctx)) {
            throw new TenantAccessDeniedException();
        }
    }

    /**
     * Listing inside a specific tenant: caller must be member of that tenant
     * (or Super Admin). HRLab Project Manager + Analyst + Consultant get
     * read-only access; CLIENT_COMPANY_ADMIN gets full management.
     */
    public void requireCanListInTenant(TenantContext ctx, UUID tenantId) {
        if (isSuperAdmin(ctx)) return;
        if (tenantId == null || !isMemberOf(ctx, tenantId)) {
            throw new TenantAccessDeniedException();
        }
    }

    /**
     * Viewing a single user is allowed when the caller and the target user
     * share at least one membership tenant. The intersection is computed
     * against {@code targetMemberships} (already loaded by the use case to
     * avoid an extra query).
     */
    public void requireCanViewUser(TenantContext ctx,
                                   List<UserTenantMembershipJpaEntity> targetMemberships) {
        if (isSuperAdmin(ctx)) return;
        Set<UUID> callerTenants = ctx.tenantMemberships();
        if (callerTenants == null || callerTenants.isEmpty()) {
            throw new TenantAccessDeniedException();
        }
        boolean overlap = targetMemberships.stream()
                .anyMatch(m -> callerTenants.contains(m.getTenantId()));
        if (!overlap) {
            throw new TenantAccessDeniedException();
        }
    }

    // ------------------------------------------------------------ MUTATIONS

    /**
     * Inviting / updating / membership-managing / role-assigning all share the
     * same tenant gate: the action must target a tenant the caller manages.
     * HRLab Super Admin bypasses; Client Company Admin is constrained to
     * its own tenant.
     */
    public void requireCanManageInTenant(TenantContext ctx, UUID tenantId) {
        if (tenantId == null) {
            throw new TenantAccessDeniedException();
        }
        if (isSuperAdmin(ctx)) return;
        // Non-super-admins must (a) be a member of the target tenant AND
        // (b) hold USER_ACCESS_MANAGE or USER_INVITE/USER_UPDATE — RBAC
        // is checked separately by @PreAuthorize at the controller layer;
        // here we only enforce the tenant-scope constraint.
        if (!isMemberOf(ctx, tenantId)) {
            throw new TenantAccessDeniedException();
        }
        if (!ctx.tenantId().equals(tenantId)) {
            // Caller's active tenant differs from the URL — refuse to let
            // CLIENT_COMPANY_ADMIN flip rows in a different tenant they
            // happen to also be a member of (defense in depth: HR Lab assigns
            // multiple memberships during onboarding migrations).
            throw new TenantAccessDeniedException();
        }
    }

    /**
     * Stable reason codes for a role being non-assignable by the caller —
     * surfaced to the frontend via the assignable-role catalog
     * ({@code GET /api/v1/roles}, slice E1) as {@code reason_if_not}.
     */
    public enum RoleAssignDenialReason {
        /** Target role uses the {@code HRLAB_*} code prefix. */
        HRLAB_ONLY,
        /** Target role has {@link RoleScope#PLATFORM} scope. */
        PLATFORM_SCOPE
    }

    /**
     * Role-assign helper. If the target role is an HRLab platform role
     * ({@code HRLAB_*}) the caller MUST additionally hold
     * {@link PermissionCodes#USER_ROLE_ASSIGN_HRLAB}; otherwise the standard
     * {@code USER_ROLE_ASSIGN} check at the controller suffices.
     *
     * <p>Throwing wrapper preserved for existing callers
     * ({@link AssignRoleUseCase}). Delegates to the non-throwing
     * {@link #canAssignRole(TenantContext, RoleJpaEntity)} so the rule lives in
     * exactly one place.
     */
    public void requireCanAssignRole(TenantContext ctx, RoleJpaEntity role) {
        if (role == null) {
            throw new TenantAccessDeniedException();
        }
        if (!canAssignRole(ctx, role)) {
            // PermissionDenied → 403, not 404: the role exists and is well-
            // known, no probing risk.
            throw new PermissionDeniedException("Action not permitted");
        }
    }

    /**
     * Non-throwing predicate capturing the role-assign rule (slice E1). Used to
     * compute {@code assignable_by_caller} in the role catalog AND, via
     * {@link #requireCanAssignRole}, to enforce the same rule on mutation.
     *
     * <p>Rule (defense-in-depth, forward-compatible): a role that is either an
     * {@code HRLAB_*} code OR carries {@link RoleScope#PLATFORM} scope requires
     * the caller to hold {@link PermissionCodes#USER_ROLE_ASSIGN_HRLAB} (granted
     * only to HRLAB_SUPER_ADMIN). Any other (tenant-scoped, non-HRLab) role is
     * assignable by a caller who already passed the controller-layer
     * {@code USER_ROLE_ASSIGN} check.
     *
     * @return {@code true} when the caller may assign {@code role}; {@code false}
     *         (with a reason available via {@link #roleAssignDenialReason}) otherwise.
     */
    public boolean canAssignRole(TenantContext ctx, RoleJpaEntity role) {
        return roleAssignDenialReason(ctx, role) == null;
    }

    /**
     * Companion to {@link #canAssignRole}: returns {@code null} when the role is
     * assignable, otherwise the stable {@link RoleAssignDenialReason}. The
     * {@code HRLAB_*} code prefix is reported as {@link RoleAssignDenialReason#HRLAB_ONLY};
     * a non-prefixed PLATFORM-scoped role is reported as
     * {@link RoleAssignDenialReason#PLATFORM_SCOPE}.
     */
    public RoleAssignDenialReason roleAssignDenialReason(TenantContext ctx, RoleJpaEntity role) {
        if (role == null) {
            return RoleAssignDenialReason.HRLAB_ONLY;
        }
        boolean hasHrlabAssign = ctx != null
                && ctx.hasPermission(PermissionCodes.USER_ROLE_ASSIGN_HRLAB);
        if (hasHrlabAssign) {
            return null; // Super Admin may assign every role.
        }
        if (isHrlabRole(role.getCode())) {
            return RoleAssignDenialReason.HRLAB_ONLY;
        }
        if (role.getScope() == RoleScope.PLATFORM) {
            return RoleAssignDenialReason.PLATFORM_SCOPE;
        }
        return null;
    }

    /**
     * Salary-permission flip gate (architecture §8.4 — strictest rule on the
     * platform). Four hard invariants:
     * <ol>
     *   <li>caller MUST hold {@link PermissionCodes#USER_SALARY_PERMISSION_TOGGLE}
     *       (granted only to CLIENT_COMPANY_ADMIN);</li>
     *   <li>target membership MUST belong to the caller's active tenant;</li>
     *   <li>caller MUST NOT flip their OWN row (no self-grant);</li>
     *   <li>target user MUST NOT hold any HRLab platform role inside the same
     *       tenant — granting salary to HRLab consultants violates the
     *       separation-of-duties model.</li>
     * </ol>
     */
    public void requireCanToggleSalaryPermission(TenantContext ctx,
                                                 UserTenantMembershipJpaEntity targetMembership,
                                                 List<RoleJpaEntity> targetRoles) {
        if (!ctx.hasPermission(PermissionCodes.USER_SALARY_PERMISSION_TOGGLE)) {
            throw new PermissionDeniedException("Action not permitted");
        }
        if (targetMembership == null
                || !targetMembership.getTenantId().equals(ctx.tenantId())) {
            throw new TenantAccessDeniedException();
        }
        if (targetMembership.getUserId().equals(ctx.userId())) {
            throw new PermissionDeniedException("Self salary-permission grant is not allowed");
        }
        if (targetRoles != null) {
            boolean hrlabAttached = targetRoles.stream()
                    .anyMatch(r -> isHrlabRole(r.getCode()));
            if (hrlabAttached) {
                throw new PermissionDeniedException(
                        "Salary permission cannot be granted to HRLab platform roles");
            }
        }
    }

    /**
     * Audit view: needs {@link PermissionCodes#AUDIT_VIEW} AND overlapping
     * membership with the target user.
     */
    public void requireCanViewAudit(TenantContext ctx,
                                    List<UserTenantMembershipJpaEntity> targetMemberships) {
        if (!ctx.hasPermission(PermissionCodes.AUDIT_VIEW)
                && !ctx.hasPermission(PermissionCodes.AUDIT_READ)) {
            throw new PermissionDeniedException("Action not permitted");
        }
        requireCanViewUser(ctx, targetMemberships);
    }

    // ------------------------------------------------------------- HELPERS

    /** True for {@code HRLAB_*} role codes — kept as a single source of truth. */
    public static boolean isHrlabRole(String roleCode) {
        return roleCode != null && roleCode.startsWith("HRLAB_");
    }

    private static boolean isSuperAdmin(TenantContext ctx) {
        return ctx != null && ctx.hasRole(RoleCodes.HRLAB_SUPER_ADMIN);
    }

    /**
     * Membership check uses the per-request cache populated by
     * {@code TenantContextFilter} (F-205) to avoid N+1 EXISTS queries; falls
     * back to a single repo lookup when called from unit tests with a
     * minimal context.
     */
    private boolean isMemberOf(TenantContext ctx, UUID tenantId) {
        if (ctx == null || ctx.userId() == null) return false;
        Set<UUID> cache = ctx.tenantMemberships();
        if (cache != null) return cache.contains(tenantId);
        return memberships.existsByUserIdAndTenantId(ctx.userId(), tenantId);
    }
}
