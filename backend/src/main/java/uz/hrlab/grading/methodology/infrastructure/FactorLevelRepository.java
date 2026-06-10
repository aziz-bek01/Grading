package uz.hrlab.grading.methodology.infrastructure;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.List;
import java.util.UUID;

/** FactorLevel repository — tenant-aware. */
public interface FactorLevelRepository
        extends TenantAwareRepository<FactorLevelJpaEntity, UUID> {

    List<FactorLevelJpaEntity>
            findAllByTenantIdAndFactorIdOrderByLevelOrderAsc(
                    UUID tenantId, UUID factorId);

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

    boolean existsByTenantIdAndFactorIdAndLevelOrder(
            UUID tenantId, UUID factorId, int levelOrder);

    void delete(FactorLevelJpaEntity entity);
}
