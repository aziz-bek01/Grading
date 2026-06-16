package uz.hrlab.grading.access.infrastructure;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.common.cache.CacheNames;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@code public.role_permissions}.
 *
 * <p>Provides the read path used to resolve the effective permission code set
 * for a set of roles (RBAC expansion). The dev authority resolver
 * ({@code DevUserAuthorityResolver}) calls
 * {@link #findPermissionCodesByRoleIds(List)} to populate the
 * {@code TenantContext} from the database when no override headers are present,
 * mirroring what the IdP normally puts into the JWT {@code permissions} claim.
 */
public interface RolePermissionRepository extends JpaRepository<RolePermissionJpaEntity, RolePermissionId> {

    /**
     * Resolve the distinct permission codes granted by the given role ids.
     *
     * <p>This is RBAC expansion only — there is NO tenant predicate because
     * {@code role_permissions} is a global catalog table (roles + permissions
     * live in the {@code public} schema and are not tenant-scoped). Tenant
     * isolation is applied one layer up: the caller resolves role ids from a
     * specific {@code user_tenant_memberships} row, so only the active tenant's
     * roles ever reach this method.
     *
     * <p><b>Cached</b> ({@link CacheNames#ROLE_PERMISSION_CODES}) — this runs on
     * every authenticated request. The cache is tenant-safe by construction: the
     * key is the sorted set of globally-unique role UUIDs
     * ({@link uz.hrlab.grading.common.cache.RolePermissionKeyGenerator}), and the
     * value (a global permission-code set) contains no tenant-scoped data. Two
     * tenants can only share a key when they reference the SAME global role,
     * whose codes are identical by definition — so no cross-tenant value can ever
     * be served. Writes to {@code role_permissions} evict via
     * {@code RolePermissionAdminUseCase}; a 60s TTL is the backstop.
     */
    @Cacheable(cacheNames = CacheNames.ROLE_PERMISSION_CODES,
            keyGenerator = "rolePermissionKeyGenerator")
    @Query("""
            select distinct p.code
            from RolePermissionJpaEntity rp
            join PermissionJpaEntity p on p.id = rp.id.permissionId
            where rp.id.roleId in :roleIds
            """)
    List<String> findPermissionCodesByRoleIds(@Param("roleIds") List<UUID> roleIds);

    /**
     * All {@code role_permissions} rows for one role (slice E2 admin API).
     *
     * <p>Used by {@code GET /api/v1/roles/{roleId}/permissions} to compute the
     * {@code granted} flag against the full permission catalog, and by the
     * replace-set mutation to diff current vs desired. No tenant predicate —
     * {@code role_permissions} is a global control-plane table (same rationale as
     * {@link #findPermissionCodesByRoleIds(List)}).
     */
    List<RolePermissionJpaEntity> findAllByIdRoleId(UUID roleId);

    /** True when {@code roleId} already grants {@code permissionId} (idempotency check). */
    boolean existsByIdRoleIdAndIdPermissionId(UUID roleId, UUID permissionId);
}
