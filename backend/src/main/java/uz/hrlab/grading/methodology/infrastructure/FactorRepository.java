package uz.hrlab.grading.methodology.infrastructure;

import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.List;
import java.util.UUID;

/** Factor repository — tenant-aware. */
public interface FactorRepository
        extends TenantAwareRepository<FactorJpaEntity, UUID> {

    List<FactorJpaEntity>
            findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(
                    UUID tenantId, UUID methodologyVersionId);

    boolean existsByTenantIdAndMethodologyVersionIdAndCode(
            UUID tenantId, UUID methodologyVersionId, String code);

    boolean existsByTenantIdAndMethodologyVersionIdAndSortOrder(
            UUID tenantId, UUID methodologyVersionId, int sortOrder);

    void delete(FactorJpaEntity entity);
}
