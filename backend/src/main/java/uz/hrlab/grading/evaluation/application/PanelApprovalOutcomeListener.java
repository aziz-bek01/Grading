package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.approval.application.ApprovalOutcomeListener;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;

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
 *   <li>CHANGES_REQUESTED → {@link ReopenPanelUseCase#onChangesRequested}
 *       (NON-destructive re-review: reopen to {@code AWAITING_EVALUATIONS}, clear
 *       stale {@code panel_factor_averages} + totals, evaluator sheets/scores
 *       PRESERVED).</li>
 *   <li>REJECTED → {@link ResetPanelToCollectingUseCase#onRejected} (DESTRUCTIVE
 *       reset to the DRAFT "Сбор экспертов" {@code COLLECTING} state). The product
 *       owner explicitly confirmed (with the "scores will be deleted" consequence)
 *       that a reject restarts evaluation: every per-evaluator sheet AND its
 *       {@code evaluation_scores} are DELETED, the {@code panel_factor_averages} +
 *       totals are cleared, and the panel returns to COLLECTING (roster mutable —
 *       {@link EvaluationPanelStatus#isRosterMutable()}). The roster
 *       {@code panel_assignments} are KEPT (reset to ASSIGNED) so experts can be
 *       re-picked and the roster re-locked. The reject REASON is captured at the
 *       approval level ({@code APPROVAL_STEP_REJECTED}, MIN_REASON_LENGTH=20) and
 *       referenced by the panel-side {@code EVALUATION_PANEL_RESET_TO_DRAFT} audit,
 *       which is the surviving record now that the scores are gone.</li>
 * </ul>
 *
 * <p>REJECT (destructive reset) and CHANGES_REQUESTED (non-destructive re-review)
 * are DISTINCT paths — they must not be collapsed onto one use case.
 */
@Component
public class PanelApprovalOutcomeListener implements ApprovalOutcomeListener {

    private final ApprovePanelUseCase approvePanel;
    private final ReopenPanelUseCase reopenPanel;
    private final ResetPanelToCollectingUseCase resetPanelToCollecting;

    public PanelApprovalOutcomeListener(ApprovePanelUseCase approvePanel,
                                        ReopenPanelUseCase reopenPanel,
                                        ResetPanelToCollectingUseCase resetPanelToCollecting) {
        this.approvePanel = approvePanel;
        this.reopenPanel = reopenPanel;
        this.resetPanelToCollecting = resetPanelToCollecting;
    }

    @Override
    public void onApprovalRequestDecided(UUID tenantId, ApprovalEntityType entityType, UUID entityId,
                                         ApprovalRequestStatus newStatus, UUID actorUserId) {
        if (entityType != ApprovalEntityType.EVALUATION_PANEL) {
            return;
        }
        switch (newStatus) {
            case APPROVED -> approvePanel.onApproved(tenantId, entityId, actorUserId);
            // NON-destructive re-review — sheets/scores preserved, only averages clear.
            case CHANGES_REQUESTED ->
                    reopenPanel.onChangesRequested(tenantId, entityId, actorUserId);
            // DESTRUCTIVE reset to COLLECTING — sheets + scores DELETED (product-confirmed).
            case REJECTED ->
                    resetPanelToCollecting.onRejected(tenantId, entityId, actorUserId);
            default -> { /* PENDING — no panel mutation */ }
        }
    }
}
