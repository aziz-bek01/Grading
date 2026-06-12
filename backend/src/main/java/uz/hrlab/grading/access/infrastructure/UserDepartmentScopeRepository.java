package uz.hrlab.grading.access.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    /**
     * BE-5 (advisory roster suggestion) — distinct (user_id, full_name) of users
     * who, in this tenant, (a) hold an ACTIVE department scope intersecting the
     * given {@code departmentIds} subtree, (b) carry the {@code roleCode} role on
     * an ACTIVE membership, and (c) have an ACTIVE membership. The single source
     * of truth for which role is "dept-scoped" lives in
     * {@code DepartmentScopePolicy.isDepartmentScoped} — the caller passes that
     * role code; this query does NOT hardcode it.
     *
     * <p>READ-ONLY + ADVISORY: the server re-validates membership on the actual
     * {@code AssignEvaluatorUseCase}; this is a UI convenience only. Tenant is
     * ALWAYS supplied from {@code TenantContext}, never input. {@code departmentIds}
     * is the already-expanded subtree (closure done by the caller via
     * {@code DepartmentRepository.findSubtreeIds} — no reimplementation).
     */
    @Query("""
            select distinct s.userId as userId, u.fullName as fullName
            from UserDepartmentScopeJpaEntity s
            join UserJpaEntity u on u.id = s.userId
            join UserTenantMembershipJpaEntity m
                 on m.userId = s.userId and m.tenantId = s.tenantId
            where s.tenantId = :tenantId
              and s.status = 'ACTIVE'
              and s.departmentId in (:departmentIds)
              and m.status = uz.hrlab.grading.access.domain.MembershipStatus.ACTIVE
              and exists (
                  select 1 from UserRoleJpaEntity ur
                  join RoleJpaEntity r on r.id = ur.roleId
                  where ur.membershipId = m.id and r.code = :roleCode)
            """)
    List<DeptDirectorCandidate> findDeptScopedCandidates(
            @Param("tenantId") UUID tenantId,
            @Param("departmentIds") Collection<UUID> departmentIds,
            @Param("roleCode") String roleCode);

    /** Projection for {@link #findDeptScopedCandidates} — id + display name only. */
    interface DeptDirectorCandidate {
        UUID getUserId();
        String getFullName();
    }
}
