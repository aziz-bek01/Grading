package uz.hrlab.grading.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.application.UserScopeExpander;
import uz.hrlab.grading.access.infrastructure.RolePermissionRepository;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
import uz.hrlab.grading.access.infrastructure.UserRoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRoleRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * DEV-ONLY authority resolver (BE-DEVAUTH-002).
 *
 * <p>When a developer authenticates through {@link DevAuthFilter} with only
 * {@code X-Dev-User} + {@code X-Dev-Tenant} (no {@code X-Dev-Roles} /
 * {@code X-Dev-Permissions} override headers), the frontend can NOT know which
 * permission codes the user holds — so the resulting {@code TenantContext}
 * would be permission-empty and every business endpoint would 403.
 *
 * <p>This resolver reproduces, against the local database, exactly what the IdP
 * normally bakes into the JWT {@code roles}/{@code permissions} claims:
 * <pre>
 *   user_tenant_memberships (user_id, tenant_id) → membership.id, salary flag
 *   user_roles  (membership_id)                  → role ids
 *   roles       (id)                             → role codes
 *   role_permissions (role ids)                  → permission codes
 * </pre>
 *
 * <p>It reuses the existing access-module repositories (no duplicated query
 * logic) and is the single source of truth for dev RBAC expansion. The
 * {@link DevAuthority} record it returns is consumed only by
 * {@link DevAuthFilter}.
 *
 * <p>SECURITY: this bean is registered ONLY under {@code local}/{@code dev}
 * profiles. It NEVER reaches a production runtime. Even if it did, it expands
 * roles for one explicit (user, tenant) pair — it cannot widen scope beyond
 * what the database already grants.
 */
@Component
@Profile({"local", "dev"})
public class DevUserAuthorityResolver {

    private static final Logger log = LoggerFactory.getLogger(DevUserAuthorityResolver.class);

    private final UserTenantMembershipRepository memberships;
    private final UserRoleRepository userRoles;
    private final RoleRepository roles;
    private final RolePermissionRepository rolePermissions;
    private final UserScopeExpander scopeExpander;

    public DevUserAuthorityResolver(UserTenantMembershipRepository memberships,
                                    UserRoleRepository userRoles,
                                    RoleRepository roles,
                                    RolePermissionRepository rolePermissions,
                                    UserScopeExpander scopeExpander) {
        this.memberships = memberships;
        this.userRoles = userRoles;
        this.roles = roles;
        this.rolePermissions = rolePermissions;
        this.scopeExpander = scopeExpander;
    }

    /**
     * Resolved RBAC + ABAC-scope snapshot for a (user, tenant) pair.
     *
     * @param roleCodes        role codes attached to the active-tenant membership
     * @param permissionCodes  permission codes expanded from those roles
     * @param salaryPermission whether the membership carries the salary gate
     * @param projectIds       ACTIVE project assignments (E4-S0)
     * @param departmentScope  ACTIVE department scope roots expanded to subtree (E4-S0)
     */
    public record DevAuthority(Set<String> roleCodes,
                               Set<String> permissionCodes,
                               boolean salaryPermission,
                               Set<UUID> projectIds,
                               Set<UUID> departmentScope) {

        public static DevAuthority empty() {
            return new DevAuthority(Set.of(), Set.of(), false, Set.of(), Set.of());
        }
    }

    /**
     * Resolve roles + permissions for the given (user, tenant). Returns
     * {@link DevAuthority#empty()} when there is no ACTIVE membership or no
     * tenant is supplied — fail-closed: an empty authority set means
     * downstream {@code @PreAuthorize} checks deny by default.
     */
    public DevAuthority resolve(UUID userId, UUID tenantId) {
        if (userId == null || tenantId == null) {
            return DevAuthority.empty();
        }
        UserTenantMembershipJpaEntity membership =
                memberships.findByUserIdAndTenantId(userId, tenantId).orElse(null);
        if (membership == null) {
            log.debug("DevUserAuthorityResolver: no membership for userId={} tenantId={}", userId, tenantId);
            return DevAuthority.empty();
        }

        // E4-S0: expand ABAC scope from the DB (no claim in the dev path → always
        // the DB fallback). Fail-closed inside the expander; an unscoped dev user
        // gets empty sets and is unaffected.
        Set<UUID> projectIds = scopeExpander.resolveProjectIds(userId, tenantId, Set.of());
        Set<UUID> departmentScope = scopeExpander.resolveDepartmentScope(userId, tenantId, Set.of());

        List<UUID> roleIds = userRoles.findAllByMembershipId(membership.getId()).stream()
                .map(UserRoleJpaEntity::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            return new DevAuthority(Set.of(), Set.of(), membership.isSalaryDataPermission(),
                    projectIds, departmentScope);
        }

        Set<String> roleCodes = roles.findAllById(roleIds).stream()
                .map(r -> r.getCode())
                .collect(Collectors.toUnmodifiableSet());

        List<String> permissionCodeList = rolePermissions.findPermissionCodesByRoleIds(roleIds);
        Set<String> permissionCodes = permissionCodeList.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(permissionCodeList.stream().collect(Collectors.toSet()));

        return new DevAuthority(roleCodes, permissionCodes, membership.isSalaryDataPermission(),
                projectIds, departmentScope);
    }
}
