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
 *   <li>CHANGES_REQUESTED → {@link ReopenPanelUseCase} (reopen to
 *       AWAITING_EVALUATIONS, clear stale averages).</li>
 *   <li>REJECTED → {@link ReopenPanelUseCase} (SAME reopen body as
 *       CHANGES_REQUESTED). A REJECT must NOT leave the panel orphaned in
 *       SUBMITTED — it sends the panel back to a re-editable state. We reuse
 *       {@code onChangesRequested} EXACTLY (no parallel reset), so the panel
 *       returns to {@code AWAITING_EVALUATIONS} with the materialized
 *       {@code panel_factor_averages} + stored totals cleared, while the
 *       evaluators' completed sheets/scores are PRESERVED. The reject
 *       reason/comment is captured by the approval-side decision
 *       ({@code APPROVAL_STEP_REJECTED}, MIN_REASON_LENGTH=20); the panel-side
 *       outcome is audited as {@code EVALUATION_PANEL_REOPENED}.
 *       <p><b>Why not the DRAFT "Сбор экспертов" (COLLECTING) state.</b> The
 *       per-evaluator evaluation sheets are created at roster-lock
 *       ({@code LockRosterUseCase}: COLLECTING → AWAITING_EVALUATIONS, one DRAFT
 *       evaluation per active assignment). Reaching COLLECTING again — where the
 *       roster is mutable and NO sheets exist
 *       ({@link EvaluationPanelStatus#isRosterMutable()}) — would necessarily
 *       DELETE every evaluator's sheet and all their scores. That destructive,
 *       surprising action is intentionally NOT performed here; the safe reopen
 *       preserves evaluator work and is the existing, well-tested path
 *       (REQ-AVG-4). Going truly destructive to COLLECTING requires explicit
 *       product confirmation.</li>
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
            // REJECTED and CHANGES_REQUESTED both reopen the panel to a re-editable
            // state via the SAME use case — a reject must not orphan the panel in
            // SUBMITTED. Sheets/scores are preserved; only the stale averages clear.
            case CHANGES_REQUESTED, REJECTED ->
                    reopenPanel.onChangesRequested(tenantId, entityId, actorUserId);
            default -> { /* PENDING — no panel mutation */ }
        }
    }
}
