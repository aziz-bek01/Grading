package uz.hrlab.grading.evaluation.infrastructure;

import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Panel assignment (roster) repository — tenant-aware (BE-2). */
public interface PanelAssignmentRepository
        extends TenantAwareRepository<PanelAssignmentJpaEntity, UUID> {

    /** Full roster of a panel (active + withdrawn) — caller filters as needed. */
    List<PanelAssignmentJpaEntity> findAllByTenantIdAndPanelId(UUID tenantId, UUID panelId);

    /** "My assignments" inbox — sheets assigned to an evaluator in a given state. */
    List<PanelAssignmentJpaEntity> findAllByTenantIdAndEvaluatorUserIdAndAssignmentStatus(
            UUID tenantId, UUID evaluatorUserId, PanelAssignmentStatus assignmentStatus);

    /** Locate a single evaluator's seat on a panel (assign/withdraw + completion sync). */
    Optional<PanelAssignmentJpaEntity> findByTenantIdAndPanelIdAndEvaluatorUserId(
            UUID tenantId, UUID panelId, UUID evaluatorUserId);

    /** Lookup a seat by its linked evaluation — completion watcher entry point. */
    Optional<PanelAssignmentJpaEntity> findByTenantIdAndEvaluationId(
            UUID tenantId, UUID evaluationId);
}
