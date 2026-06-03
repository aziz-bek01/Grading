package uz.hrlab.grading.access.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     */
    @Query("""
            select distinct p.code
            from RolePermissionJpaEntity rp
            join PermissionJpaEntity p on p.id = rp.id.permissionId
            where rp.id.roleId in :roleIds
            """)
    List<String> findPermissionCodesByRoleIds(@Param("roleIds") List<UUID> roleIds);
}
