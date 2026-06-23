package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentRepository;

import java.util.List;
import java.util.UUID;

/**
 * Single source of truth for "is this panel's roster fully evaluated" — every
 * non-WITHDRAWN {@code panel_assignment} is {@code COMPLETED}, and there is at
 * least one. Derived from the assignment set via the
 * {@link PanelAssignmentStatus#isActive()} / {@code COMPLETED} predicate — never
 * an inlined status list.
 *
 * <p>Extracted from {@code PanelCompletionWatcher} so BOTH the live completion
 * trigger (the watcher, on every score recompute) and the one-time
 * {@code BackfillPanelApprovalsMigration} sweep (which advances panels that are
 * fully evaluated but never reached the CEO) consult the SAME rule — no
 * duplication of the completion predicate.
 */
@Component
public class PanelCompletionChecker {

    private final PanelAssignmentRepository assignments;

    public PanelCompletionChecker(PanelAssignmentRepository assignments) {
        this.assignments = assignments;
    }

    /**
     * @return {@code true} iff every active (non-WITHDRAWN) assignment on the
     *         panel is {@code COMPLETED} and at least one active assignment exists.
     */
    public boolean allActiveComplete(UUID tenantId, UUID panelId) {
        List<PanelAssignmentJpaEntity> roster =
                assignments.findAllByTenantIdAndPanelId(tenantId, panelId);
        boolean anyActive = false;
        for (PanelAssignmentJpaEntity a : roster) {
            if (!a.getAssignmentStatus().isActive()) {
                continue;
            }
            anyActive = true;
            if (a.getAssignmentStatus() != PanelAssignmentStatus.COMPLETED) {
                return false;
            }
        }
        return anyActive;
    }
}
