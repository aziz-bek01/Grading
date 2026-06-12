package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.approval.application.ApprovalOutcomeListener;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;

import java.util.UUID;

/**
 * BE-10 / BE-12 wiring — the panel module's manual coupling to the CEO approval
 * decision (no event bus, same spirit as the evaluation approve coupling).
 * Reacts ONLY to {@code EVALUATION_PANEL} requests; all other entity types are
 * ignored.
 *
 * <ul>
 *   <li>APPROVED → {@link ApprovePanelUseCase} (flip APPROVED + assign grade +
 *       lock sheets).</li>
 *   <li>CHANGES_REQUESTED → {@link ReopenPanelUseCase} (reopen to
 *       AWAITING_EVALUATIONS, clear stale averages).</li>
 *   <li>REJECTED → no panel mutation (the panel stays SUBMITTED as a record;
 *       a reject is terminal on the request and the panel can be archived
 *       separately). Audited at the approval level.</li>
 * </ul>
 */
@Component
public class PanelApprovalOutcomeListener implements ApprovalOutcomeListener {

    private final ApprovePanelUseCase approvePanel;
    private final ReopenPanelUseCase reopenPanel;

    public PanelApprovalOutcomeListener(ApprovePanelUseCase approvePanel,
                                        ReopenPanelUseCase reopenPanel) {
        this.approvePanel = approvePanel;
        this.reopenPanel = reopenPanel;
    }

    @Override
    public void onApprovalRequestDecided(UUID tenantId, ApprovalEntityType entityType, UUID entityId,
                                         ApprovalRequestStatus newStatus, UUID actorUserId) {
        if (entityType != ApprovalEntityType.EVALUATION_PANEL) {
            return;
        }
        switch (newStatus) {
            case APPROVED -> approvePanel.onApproved(tenantId, entityId, actorUserId);
            case CHANGES_REQUESTED -> reopenPanel.onChangesRequested(tenantId, entityId, actorUserId);
            default -> { /* REJECTED / PENDING — no panel mutation */ }
        }
    }
}
