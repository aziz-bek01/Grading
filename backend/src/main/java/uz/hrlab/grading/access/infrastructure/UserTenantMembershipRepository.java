package uz.hrlab.grading.access.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Memberships are control-plane but always queried with tenant + user
 * — we expose tenant-scoped accessors only (security-blueprint §5.2 pattern).
 */
public interface UserTenantMembershipRepository
        extends JpaRepository<UserTenantMembershipJpaEntity, UUID> {

    Optional<UserTenantMembershipJpaEntity> findByUserIdAndTenantId(UUID userId, UUID tenantId);

    List<UserTenantMembershipJpaEntity> findAllByUserId(UUID userId);

    List<UserTenantMembershipJpaEntity> findAllByTenantId(UUID tenantId);

    boolean existsByUserIdAndTenantId(UUID userId, UUID tenantId);
}
