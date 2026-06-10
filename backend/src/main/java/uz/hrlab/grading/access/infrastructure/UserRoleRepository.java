package uz.hrlab.grading.access.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
