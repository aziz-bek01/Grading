package uz.hrlab.grading.methodology.infrastructure;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** FactorLevel repository — tenant-aware. */
public interface FactorLevelRepository
        extends TenantAwareRepository<FactorLevelJpaEntity, UUID> {

    List<FactorLevelJpaEntity>
            findAllByTenantIdAndFactorIdOrderByLevelOrderAsc(
                    UUID tenantId, UUID factorId);

    /**
     * BE-26 — batch variant of {@link #findAllByTenantIdAndFactorIdOrderByLevelOrderAsc}
     * for the report methodology-spec build: loads every level of a PAGE of factors
     * in ONE tenant-scoped round-trip (no per-factor N+1), globally ordered by
     * {@code level_order ASC} so an in-memory group-by-factorId preserves the same
     * per-factor level order the single-factor finder produced. Tenant is pinned.
     */
    List<FactorLevelJpaEntity>
            findAllByTenantIdAndFactorIdInOrderByLevelOrderAsc(
                    UUID tenantId, Collection<UUID> factorIds);

    /**
     * BE-4 — ACTIVE (non-deprecated) levels of a factor, for the "levels a NEW
     * evaluation can pick" path. Excludes soft-deprecated rows; backed by the
     * partial index {@code idx_factor_levels_active_factor}. Historical recompute
     * keeps using the unfiltered finder above.
     */
    List<FactorLevelJpaEntity>
            findAllByTenantIdAndFactorIdAndDeprecatedAtIsNullOrderByLevelOrderAsc(
                    UUID tenantId, UUID factorId);

    /**
     * Batch variant of {@link #findAllByTenantIdAndFactorIdAndDeprecatedAtIsNullOrderByLevelOrderAsc}
     * — loads the ACTIVE levels of a set of factors in ONE tenant-scoped round-trip
     * (no per-factor N+1 on the new-evaluation preview path,
     * {@code EvaluationContextLoader#loadActiveLevels}). Excludes soft-deprecated rows
     * and is globally ordered by {@code level_order ASC} so an in-memory
     * group-by-factorId preserves each factor's per-level order byte-for-byte, exactly
     * as the single-factor finder produced (mirrors the BE-26 batch above). Tenant is
     * pinned.
     */
    List<FactorLevelJpaEntity>
            findAllByTenantIdAndFactorIdInAndDeprecatedAtIsNullOrderByLevelOrderAsc(
                    UUID tenantId, Collection<UUID> factorIds);

    /**
     * Highest {@code level_order} currently assigned to this factor's levels
     * (tenant-scoped), or {@code null} when the factor has no levels yet. Used
     * by {@code FactorLevelService.add(...)} to compute a collision-free
     * {@code max+1} order server-side (defect: client-supplied 0-based order
     * collided with the 1-indexed orders of template-created levels →
     * {@code uq_factor_levels_factor_level_order} 23505).
     */
    @Query("select max(l.levelOrder) from FactorLevelJpaEntity l "
            + "where l.tenantId = :tenantId and l.factorId = :factorId")
    Integer findMaxLevelOrderByFactorId(@Param("tenantId") UUID tenantId,
                                        @Param("factorId") UUID factorId);

    boolean existsByTenantIdAndFactorIdAndCode(
            UUID tenantId, UUID factorId, String code);

    /**
     * Tenant-scoped resolution of a level by its {@code code} within a factor —
     * the find-half of the import find-or-create upsert
     * ({@code MethodologyFactorsRowCommitter}). Tenant is pinned so a foreign
     * row never resolves.
     */
    Optional<FactorLevelJpaEntity> findByTenantIdAndFactorIdAndCode(
            UUID tenantId, UUID factorId, String code);

    boolean existsByTenantIdAndFactorIdAndLevelOrder(
            UUID tenantId, UUID factorId, int levelOrder);

    void delete(FactorLevelJpaEntity entity);

    /** Force pending SQL so an FK violation (23503) surfaces here, not at commit. */
    void flush();
}
