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

    long deleteByIdAndMembershipId(UUID id, UUID membershipId);
}
