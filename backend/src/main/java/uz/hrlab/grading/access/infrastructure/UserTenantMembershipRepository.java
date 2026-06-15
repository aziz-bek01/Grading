package uz.hrlab.grading.access.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Memberships are control-plane but always queried with tenant + user
 * — we expose tenant-scoped accessors only (security-blueprint §5.2 pattern).
 *
 * <p>User management module (MVP 1 Phase 1.5) adds tenant-scoped finders for
 * the admin endpoints. {@code findByIdAndTenantId} is the canonical accessor
 * for {@code DELETE /api/v1/users/{id}/memberships/{tenantId}} — it MUST be
 * used instead of {@link JpaRepository#findById(Object)} to prevent
 * cross-tenant membership probing.
 */
public interface UserTenantMembershipRepository
        extends JpaRepository<UserTenantMembershipJpaEntity, UUID> {

    Optional<UserTenantMembershipJpaEntity> findByUserIdAndTenantId(UUID userId, UUID tenantId);

    List<UserTenantMembershipJpaEntity> findAllByUserId(UUID userId);

    List<UserTenantMembershipJpaEntity> findAllByTenantId(UUID tenantId);

    /**
     * PERF (P1) — tenant-scoped batch lookup of memberships for a known set of
     * user ids (e.g. the few actor ids harvested from a page of approval rows by
     * {@code ActorNameResolver}). Replaces {@code findAllByTenantId(..)} +
     * in-memory filter, which loaded the WHOLE tenant membership table per
     * request. {@code tenant_id} stays pinned in the predicate, so a user id from
     * another tenant simply contributes no row — never widens scope.
     */
    List<UserTenantMembershipJpaEntity> findByTenantIdAndUserIdIn(
            UUID tenantId, Collection<UUID> userIds);

    boolean existsByUserIdAndTenantId(UUID userId, UUID tenantId);

    long countByTenantId(UUID tenantId);

    /**
     * Paged tenant-scoped membership search with optional status / role-code /
     * free-text filters. Used by {@code GET /api/v1/users?tenantId=&...}.
     *
     * <p>The {@code search} parameter is matched case-insensitively against
     * {@code users.email} and {@code users.full_name}. {@code roleCode} joins
     * through {@code user_roles → roles}. {@code search}/{@code roleCode}
     * accept {@code null} as "skip"; callers MUST route through the default
     * dispatcher {@link #searchInTenant} which picks the correct overload —
     * Postgres' JDBC driver cannot infer the type of an untyped {@code NULL}
     * passed to {@code lower(?)} / {@code concat('%', ?, '%')}
     * (it defaults to {@code bytea} and throws
     * {@code function lower(bytea) does not exist}). The four overloads
     * eliminate the conditional binding altogether.
     *
     * <p>SECURITY: {@code tenantId} is REQUIRED — never call any of these with
     * a null tenantId because that would return cross-tenant rows.
     */
    default Page<UserTenantMembershipJpaEntity> searchInTenant(
            UUID tenantId,
            uz.hrlab.grading.access.domain.MembershipStatus status,
            String search,
            String roleCode,
            Pageable pageable) {
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasRole = roleCode != null && !roleCode.isBlank();
        if (hasSearch && hasRole) {
            return searchInTenantWithSearchAndRole(tenantId, status, search, roleCode, pageable);
        }
        if (hasSearch) {
            return searchInTenantWithSearch(tenantId, status, search, pageable);
        }
        if (hasRole) {
            return searchInTenantWithRole(tenantId, status, roleCode, pageable);
        }
        return searchInTenantNoText(tenantId, status, pageable);
    }

    @Query("""
            select distinct m from UserTenantMembershipJpaEntity m
            join UserJpaEntity u on u.id = m.userId
            where m.tenantId = :tenantId
              and (:status is null or m.status = :status)
            """)
    Page<UserTenantMembershipJpaEntity> searchInTenantNoText(
            @Param("tenantId") UUID tenantId,
            @Param("status") uz.hrlab.grading.access.domain.MembershipStatus status,
            Pageable pageable);

    @Query("""
            select distinct m from UserTenantMembershipJpaEntity m
            join UserJpaEntity u on u.id = m.userId
            where m.tenantId = :tenantId
              and (:status is null or m.status = :status)
              and (lower(u.email) like lower(concat('%', :search, '%'))
                   or lower(u.fullName) like lower(concat('%', :search, '%')))
            """)
    Page<UserTenantMembershipJpaEntity> searchInTenantWithSearch(
            @Param("tenantId") UUID tenantId,
            @Param("status") uz.hrlab.grading.access.domain.MembershipStatus status,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            select distinct m from UserTenantMembershipJpaEntity m
            join UserJpaEntity u on u.id = m.userId
            where m.tenantId = :tenantId
              and (:status is null or m.status = :status)
              and exists (
                  select 1 from UserRoleJpaEntity ur
                  join RoleJpaEntity r on r.id = ur.roleId
                  where ur.membershipId = m.id and r.code = :roleCode)
            """)
    Page<UserTenantMembershipJpaEntity> searchInTenantWithRole(
            @Param("tenantId") UUID tenantId,
            @Param("status") uz.hrlab.grading.access.domain.MembershipStatus status,
            @Param("roleCode") String roleCode,
            Pageable pageable);

    @Query("""
            select distinct m from UserTenantMembershipJpaEntity m
            join UserJpaEntity u on u.id = m.userId
            where m.tenantId = :tenantId
              and (:status is null or m.status = :status)
              and (lower(u.email) like lower(concat('%', :search, '%'))
                   or lower(u.fullName) like lower(concat('%', :search, '%')))
              and exists (
                  select 1 from UserRoleJpaEntity ur
                  join RoleJpaEntity r on r.id = ur.roleId
                  where ur.membershipId = m.id and r.code = :roleCode)
            """)
    Page<UserTenantMembershipJpaEntity> searchInTenantWithSearchAndRole(
            @Param("tenantId") UUID tenantId,
            @Param("status") uz.hrlab.grading.access.domain.MembershipStatus status,
            @Param("search") String search,
            @Param("roleCode") String roleCode,
            Pageable pageable);
}
