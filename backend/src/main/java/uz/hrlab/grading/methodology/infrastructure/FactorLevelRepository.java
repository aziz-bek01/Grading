package uz.hrlab.grading.methodology.infrastructure;

import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.List;
import java.util.UUID;

/** FactorLevel repository — tenant-aware. */
public interface FactorLevelRepository
        extends TenantAwareRepository<FactorLevelJpaEntity, UUID> {

    List<FactorLevelJpaEntity>
            findAllByTenantIdAndFactorIdOrderByLevelOrderAsc(
                    UUID tenantId, UUID factorId);

    boolean existsByTenantIdAndFactorIdAndCode(
            UUID tenantId, UUID factorId, String code);

    boolean existsByTenantIdAndFactorIdAndLevelOrder(
            UUID tenantId, UUID factorId, int levelOrder);

    void delete(FactorLevelJpaEntity entity);
}
