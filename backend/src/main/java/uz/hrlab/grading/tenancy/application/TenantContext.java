package uz.hrlab.grading.tenancy.application;

import java.util.Set;
import java.util.UUID;

/**
 * Immutable per-request security/tenant context derived from the validated
 * Authentication (architecture §8.1, security-blueprint §5.1).
 *
 * <p>Backend code MUST always source {@code tenantId} from this context —
 * NEVER from request body/path/query (security-blueprint §20.1 finding 2).
 *
 * @param userId             authenticated user id (JWT {@code sub} mapped to {@code public.users.id})
 * @param tenantId           active tenant for this request
 * @param projectIds         projects the user is assigned to within the active tenant
 * @param roles              role codes from JWT (validated against catalog)
 * @param permissions        permission codes from JWT
 * @param departmentScope    department ids the user can see (manager-scope ABAC)
 * @param salaryPermission   whether {@code SALARY_*} permissions apply
 * @param locale             preferred UI locale
 * @param tenantMemberships  per-request cache of {@code user_tenant_memberships}
 *                           tenants for this user (F-205). Populated once by
 *                           {@code TenantContextFilter}; consulted by
 *                           {@code ConsultantTenantAssignmentPolicy} to avoid
 *                           N+1 EXISTS queries. May be {@code null} when the
 *                           context is constructed in unit tests — policies
 *                           must fall back to the repository in that case.
 */
public record TenantContext(
        UUID userId,
        UUID tenantId,
        Set<UUID> projectIds,
        Set<String> roles,
        Set<String> permissions,
        Set<UUID> departmentScope,
        boolean salaryPermission,
        String locale,
        Set<UUID> tenantMemberships
) {
    /**
     * Back-compat constructor — preserves the pre-F-205 8-arg shape used by
     * dev-auth filter, tests, and tenancy resolver call sites that have not
     * been migrated yet. The membership cache is left {@code null} so the
     * policy falls back to a single repository lookup.
     */
    public TenantContext(UUID userId, UUID tenantId, Set<UUID> projectIds,
                         Set<String> roles, Set<String> permissions,
                         Set<UUID> departmentScope, boolean salaryPermission,
                         String locale) {
        this(userId, tenantId, projectIds, roles, permissions, departmentScope,
                salaryPermission, locale, null);
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
}
