package uz.hrlab.grading.access.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@code public.user_department_scopes} (E4-S0).
 *
 * <p>Control-plane mapping table — extends {@link JpaRepository} like its
 * siblings. Cross-tenant isolation is enforced by EXPLICIT {@code tenant_id} +
 * {@code user_id} predicates on every finder; tenant always comes from
 * {@code TenantContext}, never input. No bare {@code department_id}-only lookup
 * is exposed (BOLA defense), mirroring {@link UserRoleRepository}.
 */
public interface UserDepartmentScopeRepository
        extends JpaRepository<UserDepartmentScopeJpaEntity, UUID> {

    /**
     * Department ids the user is ACTIVE-scoped to in this tenant. These are the
     * SCOPE ROOTS — the resolver expands them to the department subtree. Used on
     * the auth path as a single query (no N+1).
     */
    @Query("""
            select s.departmentId from UserDepartmentScopeJpaEntity s
            where s.userId = :userId and s.tenantId = :tenantId
              and s.status = 'ACTIVE'
            """)
    List<UUID> findActiveDepartmentIds(@Param("userId") UUID userId,
                                       @Param("tenantId") UUID tenantId);

    /** All rows (ACTIVE + REVOKED) for a (user, tenant) — used by the assign API. */
    List<UserDepartmentScopeJpaEntity> findAllByUserIdAndTenantId(UUID userId, UUID tenantId);

    Optional<UserDepartmentScopeJpaEntity> findByUserIdAndTenantIdAndDepartmentId(
            UUID userId, UUID tenantId, UUID departmentId);
}
