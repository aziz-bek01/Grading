package uz.hrlab.grading.evaluation.infrastructure;

import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.List;
import java.util.UUID;

/** Panel per-factor average repository — tenant-aware (BE-2). */
public interface PanelFactorAverageRepository
        extends TenantAwareRepository<PanelFactorAverageJpaEntity, UUID> {

    List<PanelFactorAverageJpaEntity> findAllByTenantIdAndPanelId(UUID tenantId, UUID panelId);

    /** Tenant-scoped bulk delete used on recompute / reopen (stale averages cleared). */
    long deleteAllByTenantIdAndPanelId(UUID tenantId, UUID panelId);
}
