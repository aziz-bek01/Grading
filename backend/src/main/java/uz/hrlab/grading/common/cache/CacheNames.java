package uz.hrlab.grading.common.cache;

/**
 * Canonical Caffeine cache names (single source of truth for {@code @Cacheable}
 * / {@code @CacheEvict} {@code value} attributes).
 *
 * <p>Only caches listed here are pre-registered by {@link CacheConfig}. A typo
 * in a {@code @Cacheable("...")} string would otherwise silently create a
 * second, unbounded, never-expiring cache — referencing a constant keeps the
 * annotation sites and the {@link CacheConfig} policy in lock-step.
 *
 * <h3>Tenant-safety note</h3>
 * Every cache name here is documented with WHY its key cannot collide across
 * tenants (see the JavaDoc on each constant). A cache may only be added if its
 * key is either a globally-unique UUID set OR an explicit composite that
 * includes the tenant id. Salary, evaluation scores and any value scoped by a
 * non-unique natural key are NEVER cached.
 */
public final class CacheNames {

    private CacheNames() { }

    /**
     * Role-id-set → distinct permission codes (RBAC expansion).
     *
     * <p>Backs {@code RolePermissionRepository.findPermissionCodesByRoleIds},
     * which runs on EVERY authenticated request (dev + JWT authority resolve).
     *
     * <p><b>Tenant-safe by construction:</b> {@code public.roles} is a GLOBAL
     * control-plane table; role ids are globally-unique UUIDs. The cache key is
     * the <em>sorted</em> set of those UUIDs (see
     * {@link RolePermissionKeyGenerator}), so the same role-id set always maps
     * to the same value regardless of which tenant's membership produced it —
     * and two different tenants can only ever share a key if they literally
     * reference the SAME global role, in which case the permission-code set is
     * identical by definition. There is no tenant-scoped data in either the key
     * or the value, so no cross-tenant value can be served.
     */
    public static final String ROLE_PERMISSION_CODES = "rolePermissionCodes";
}
