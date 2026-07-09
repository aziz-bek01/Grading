package uz.hrlab.grading.evaluation.infrastructure;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Panel per-factor average repository — tenant-aware (BE-2). */
public interface PanelFactorAverageRepository
        extends TenantAwareRepository<PanelFactorAverageJpaEntity, UUID> {

    List<PanelFactorAverageJpaEntity> findAllByTenantIdAndPanelId(UUID tenantId, UUID panelId);

    /**
     * BE-19 — batch variant of {@link #findAllByTenantIdAndPanelId} for the report
     * panel-averages build: loads every materialized per-factor average for a PAGE
     * of panels in ONE tenant-scoped round-trip (no per-panel N+1), grouped by
     * {@code panel_id} in memory. Mirrors the batch pattern already used in
     * {@code DefaultReportDataPort.loadEvaluations} (scores/positions IdIn). Tenant
     * is pinned, so a foreign panel id contributes no row.
     */
    List<PanelFactorAverageJpaEntity> findAllByTenantIdAndPanelIdIn(
            UUID tenantId, Collection<UUID> panelIds);

    /** Tenant-scoped bulk delete used on recompute / reopen (stale averages cleared). */
    long deleteAllByTenantIdAndPanelId(UUID tenantId, UUID panelId);

    /**
     * P0-B display-integrity — the per-panel "contributing evaluators" denominator
     * for a PAGE of panels in ONE tenant-scoped round-trip (no N+1 on the list
     * surface). {@code evaluator_count} is the number of COMPLETED sheets that fed
     * the mean ({@link ComputePanelAverageUseCase} writes the SAME count on every
     * factor row of a panel), so {@code MAX(evaluatorCount)} per panel is the
     * deterministic denominator. Panels with no materialized average (still
     * collecting, or reopened with averages cleared) simply contribute no row.
     * The {@code tenant_id} bind keeps it BOLA-safe (a foreign panel id yields
     * nothing).
     */
    @Query("""
           SELECT a.panelId AS panelId, MAX(a.evaluatorCount) AS evaluatorCount
           FROM PanelFactorAverageJpaEntity a
           WHERE a.tenantId = :tenantId AND a.panelId IN (:panelIds)
           GROUP BY a.panelId
           """)
    List<PanelEvaluatorCountView> findEvaluatorCountsByPanelIds(
            @Param("tenantId") UUID tenantId,
            @Param("panelIds") Collection<UUID> panelIds);

    /** Projection for {@link #findEvaluatorCountsByPanelIds}. */
    interface PanelEvaluatorCountView {
        UUID getPanelId();
        int getEvaluatorCount();
    }
}
