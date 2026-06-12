package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.infrastructure.RolePermissionRepository;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
import uz.hrlab.grading.access.infrastructure.UserRoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRoleRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Expands the RBAC (role codes + permission codes + salary gate) for a SINGLE
 * {@code user_tenant_memberships} row.
 *
 * <h3>Why this exists (BE-3-FIX, 2026-06-12)</h3>
 * The same membership→roles→permissions expansion was duplicated in
 * {@code JwtTenantContextResolver} and {@code DevUserAuthorityResolver}, and was
 * needed in a THIRD place — the {@code /users/me} identity payload — to let the
 * SPA shell render for a multi-membership super admin whose active tenant is
 * ambiguous on the very first request. Centralising it here keeps all three
 * paths byte-for-byte identical (same query path, same DB state → same set) and
 * makes the security-critical "permissions are always for ONE real tenant"
 * invariant easy to audit in one place.
 *
 * <p>SECURITY: the expansion is always scoped to a SINGLE membership (one
 * tenant). It can NEVER union permissions across tenants — that would re-open a
 * cross-tenant permission bleed. Callers pass exactly one membership.
 */
@Component
public class MembershipAuthorityResolver {

    private final UserRoleRepository userRoles;
    private final RoleRepository roles;
    private final RolePermissionRepository rolePermissions;

    public MembershipAuthorityResolver(UserRoleRepository userRoles,
                                       RoleRepository roles,
                                       RolePermissionRepository rolePermissions) {
        this.userRoles = userRoles;
        this.roles = roles;
        this.rolePermissions = rolePermissions;
    }

    /**
     * Snapshot of the RBAC expanded for one membership.
     *
     * @param roleCodes        role codes attached to the membership
     * @param permissionCodes  permission codes expanded from those roles
     * @param salaryPermission whether the membership carries the salary gate
     */
    public record Authority(Set<String> roleCodes,
                            Set<String> permissionCodes,
                            boolean salaryPermission) {

        public static Authority empty() {
            return new Authority(Set.of(), Set.of(), false);
        }
    }

    /**
     * Expand role codes + permission codes for a membership. Returns a
     * permission-empty (but salary-flag-honouring) authority when the membership
     * has no roles. Fail-closed: a {@code null} membership yields
     * {@link Authority#empty()}.
     */
    public Authority expand(UserTenantMembershipJpaEntity membership) {
        if (membership == null) {
            return Authority.empty();
        }
        List<UUID> roleIds = userRoles.findAllByMembershipId(membership.getId()).stream()
                .map(UserRoleJpaEntity::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            return new Authority(Set.of(), Set.of(), membership.isSalaryDataPermission());
        }
        Set<String> roleCodes = roles.findAllById(roleIds).stream()
                .map(r -> r.getCode())
                .collect(Collectors.toUnmodifiableSet());
        List<String> permissionCodeList = rolePermissions.findPermissionCodesByRoleIds(roleIds);
        Set<String> permissionCodes = permissionCodeList.isEmpty()
                ? Set.of()
                : Set.copyOf(permissionCodeList);
        return new Authority(roleCodes, permissionCodes, membership.isSalaryDataPermission());
    }
}
