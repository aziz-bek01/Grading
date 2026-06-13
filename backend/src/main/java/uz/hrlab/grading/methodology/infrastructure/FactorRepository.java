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

    /**
     * BE-4 — ACTIVE (non-deprecated) factors of a version, for the "factors a
     * NEW evaluation can score" path. Excludes soft-deprecated rows
     * ({@code deprecated_at IS NOT NULL}); backed by the partial index
     * {@code idx_factors_active_version}. Historical recompute keeps using the
     * unfiltered finder above so a deprecated factor a past evaluation already
     * scored still resolves.
     */
    List<FactorJpaEntity>
            findAllByTenantIdAndMethodologyVersionIdAndDeprecatedAtIsNullOrderBySortOrderAsc(
                    UUID tenantId, UUID methodologyVersionId);

    boolean existsByTenantIdAndMethodologyVersionIdAndCode(
            UUID tenantId, UUID methodologyVersionId, String code);

    boolean existsByTenantIdAndMethodologyVersionIdAndSortOrder(
            UUID tenantId, UUID methodologyVersionId, int sortOrder);

    void delete(FactorJpaEntity entity);

    /** Force pending SQL so an FK violation (23503) surfaces here, not at commit. */
    void flush();
}
