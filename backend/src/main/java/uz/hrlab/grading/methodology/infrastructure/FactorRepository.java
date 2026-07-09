package uz.hrlab.grading.methodology.infrastructure;

import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Factor repository — tenant-aware. */
public interface FactorRepository
        extends TenantAwareRepository<FactorJpaEntity, UUID> {

    List<FactorJpaEntity>
            findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(
                    UUID tenantId, UUID methodologyVersionId);

    /**
     * BE-24 — batch by-id resolution (tenant-scoped, no N+1). Mirrors the
     * {@code findAllByTenantIdAndIdIn} pattern used across the read surfaces
     * (positions/departments/methodology-versions): a page of distinct factor ids
     * resolves in ONE round-trip. Tenant is pinned, so a foreign id contributes no
     * row (never the BOLA-prone {@code findAllById}).
     */
    List<FactorJpaEntity> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    /**
     * BE-25 — count-only finder for the "filled / N" denominator. Replaces loading
     * every factor row just to {@code .size()} them: the DB returns a scalar count
     * instead of hydrating the (weight/max_points/i18n) rows. Counts ALL factors of
     * the version (deprecated included) — byte-identical to the previous
     * {@code findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(...).size()}.
     */
    long countByTenantIdAndMethodologyVersionId(UUID tenantId, UUID methodologyVersionId);

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
