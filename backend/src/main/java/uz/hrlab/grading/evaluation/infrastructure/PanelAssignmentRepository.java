package uz.hrlab.grading.evaluation.infrastructure;

import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Panel assignment (roster) repository — tenant-aware (BE-2). */
public interface PanelAssignmentRepository
        extends TenantAwareRepository<PanelAssignmentJpaEntity, UUID> {

    /** Full roster of a panel (active + withdrawn) — caller filters as needed. */
    List<PanelAssignmentJpaEntity> findAllByTenantIdAndPanelId(UUID tenantId, UUID panelId);

    /**
     * PERF — batch variant of {@link #findAllByTenantIdAndPanelId} for the panel
     * LIST surface: load the rosters of a whole page of panels in ONE round-trip
     * instead of one query per panel (kills the N+1 in {@code PanelQueries.list}).
     * Tenant-scoped — {@code tenant_id} is always pinned, so a panel id belonging
     * to another tenant simply contributes no rows (no widening). The caller groups
     * the flat result by {@code panel_id} in memory.
     */
    List<PanelAssignmentJpaEntity> findAllByTenantIdAndPanelIdIn(
            UUID tenantId, Collection<UUID> panelIds);

    /** "My assignments" inbox — sheets assigned to an evaluator in a given state. */
    List<PanelAssignmentJpaEntity> findAllByTenantIdAndEvaluatorUserIdAndAssignmentStatus(
            UUID tenantId, UUID evaluatorUserId, PanelAssignmentStatus assignmentStatus);

    /** Locate a single evaluator's seat on a panel (assign/withdraw + completion sync). */
    Optional<PanelAssignmentJpaEntity> findByTenantIdAndPanelIdAndEvaluatorUserId(
            UUID tenantId, UUID panelId, UUID evaluatorUserId);

    /** Lookup a seat by its linked evaluation — completion watcher entry point. */
    Optional<PanelAssignmentJpaEntity> findByTenantIdAndEvaluationId(
            UUID tenantId, UUID evaluationId);

    /**
     * Tenant-scoped bulk delete of a panel's roster seats (Defect-2 BE). Used by
     * {@code DeletePanelUseCase} to remove dependent {@code panel_assignments}
     * rows BEFORE the parent panel — keeping the delete deterministic at the app
     * layer even though the {@code ON DELETE CASCADE} FK would also remove them.
     * Mirrors how {@code DeleteEvaluationUseCase} clears dependent score rows
     * first. The {@code tenant_id} predicate keeps the delete BOLA-safe. Returns
     * the number of rows deleted.
     */
    long deleteAllByTenantIdAndPanelId(UUID tenantId, UUID panelId);
}
