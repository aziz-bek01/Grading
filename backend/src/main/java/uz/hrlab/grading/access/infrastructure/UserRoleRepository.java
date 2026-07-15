package uz.hrlab.grading.access.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@code public.user_roles}.
 *
 * <p>Always queried by membership — we deliberately do NOT expose
 * {@code findById} aliases that take only a role_id or user_id without the
 * membership filter, since a {@code (user_id, role_id)} lookup that bypasses
 * the tenant boundary would allow cross-tenant role probing (BOLA).
 */
public interface UserRoleRepository extends JpaRepository<UserRoleJpaEntity, UUID> {

    List<UserRoleJpaEntity> findAllByMembershipId(UUID membershipId);

    Optional<UserRoleJpaEntity> findByIdAndMembershipId(UUID id, UUID membershipId);

    Optional<UserRoleJpaEntity> findByMembershipIdAndRoleId(UUID membershipId, UUID roleId);

    boolean existsByMembershipIdAndRoleId(UUID membershipId, UUID roleId);

    /**
     * True when {@code roleId} is currently attached to ANY membership (slice E3
     * delete-in-use guard). A custom role with at least one live assignment may
     * NOT be deleted — the admin must revoke it from every user first, so no user
     * silently loses access when the role disappears. Not membership-scoped on
     * purpose: a custom role lives in one tenant, so every {@code user_roles} row
     * pointing at it already belongs to that tenant's memberships.
     */
    boolean existsByRoleId(UUID roleId);

    long deleteByIdAndMembershipId(UUID id, UUID membershipId);

    /**
     * Fix A (platform super-admin cross-tenant switch) — the DB-derived
     * platform-super-admin predicate. TRUE when {@code userId} holds the given
     * SYSTEM role code (e.g. {@code HRLAB_SUPER_ADMIN}) via a {@code user_roles}
     * row attached to at least one ACTIVE {@code user_tenant_memberships} row.
     *
     * <p>SECURITY: computed PURELY from the user's REAL role grants
     * ({@code user_roles → roles}) on REAL ACTIVE memberships — NEVER from a JWT
     * claim, request header, or the requested/target tenant — so it cannot be
     * spoofed by a client. It is scoped to the SINGLE role code the caller passes
     * (never widened to another role), and {@code r.isSystem = true} pins it to the
     * seeded PLATFORM role: a tenant CUSTOM role can never reuse a system code
     * (rejected in {@code CustomRoleUseCase}), so this is defense in depth against
     * a look-alike custom role. A REVOKED/SUSPENDED membership does not count
     * ({@code m.status = ACTIVE}), so revoking the super admin's membership
     * immediately revokes the predicate.
     */
    @Query("""
            select (count(ur) > 0) from UserRoleJpaEntity ur
            join UserTenantMembershipJpaEntity m on m.id = ur.membershipId
            join RoleJpaEntity r on r.id = ur.roleId
            where m.userId = :userId
              and m.status = uz.hrlab.grading.access.domain.MembershipStatus.ACTIVE
              and r.code = :roleCode
              and r.isSystem = true
            """)
    boolean existsActiveSystemRoleHolder(@Param("userId") UUID userId,
                                         @Param("roleCode") String roleCode);
}
