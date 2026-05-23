package uz.hrlab.grading.tenancy.infrastructure;

import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Client companies are tenant-scoped data: every row carries a
 * {@code tenant_id} and the only legal access path is tenant-aware
 * (security-blueprint §5.2, finding F-01).
 *
 * <p>Extends {@link TenantAwareRepository} so the BOLA-prone
 * {@code findById}/{@code findAll}/{@code delete} methods inherited from
 * {@code JpaRepository} are NOT available. ArchUnit enforces the inverse: no
 * tenant-data repository may extend {@code JpaRepository}.
 */
public interface ClientCompanyRepository
        extends TenantAwareRepository<ClientCompanyJpaEntity, UUID> {

    Optional<ClientCompanyJpaEntity> findByTenantId(UUID tenantId);

    boolean existsByTenantId(UUID tenantId);
}
