package uz.hrlab.grading.tenancy.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Tenants are control-plane data — repository may expose {@code findById}.
 * Tenant business-data repositories MUST NOT (see security blueprint §5.2).
 */
public interface TenantRepository extends JpaRepository<TenantJpaEntity, UUID> {

    Optional<TenantJpaEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
