package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * CEO inline sign-off — panel outcome wiring. The three terminal decisions drive
 * THREE DISTINCT use cases:
 * <ul>
 *   <li>APPROVED → {@link ApprovePanelUseCase} (grade + lock);</li>
 *   <li>CHANGES_REQUESTED → {@link ReopenPanelUseCase#onChangesRequested}
 *       (NON-destructive: AWAITING_EVALUATIONS, scores preserved);</li>
 *   <li>REJECTED → {@link ResetPanelToCollectingUseCase#onRejected} (DESTRUCTIVE:
 *       reset to COLLECTING, sheets + scores DELETED — product-confirmed).</li>
 * </ul>
 * The actual status/score/average mutation + audit are proven by
 * {@link ResetPanelToCollectingUseCaseTest} / {@link ReopenPanelUseCaseTest} /
 * {@link ApprovePanelUseCaseTest}; here we prove the WIRING — each decision drives
 * the correct use case and the other two are NOT touched, REJECT and
 * CHANGES_REQUESTED stay distinct, and a non-panel entity type is ignored.
 */
@ExtendWith(MockitoExtension.class)
class PanelApprovalOutcomeListenerTest {

    @Mock ApprovePanelUseCase approvePanel;
    @Mock ReopenPanelUseCase reopenPanel;
    @Mock ResetPanelToCollectingUseCase resetPanelToCollecting;

    PanelApprovalOutcomeListener listener;

    final UUID tenantId = UUID.randomUUID();
    final UUID panelId = UUID.randomUUID();
    final UUID actorUserId = UUID.randomUUID();

    private PanelApprovalOutcomeListener listener() {
        return new PanelApprovalOutcomeListener(approvePanel, reopenPanel, resetPanelToCollecting);
    }

    @Test
    void rejectedDestructivelyResetsPanelToCollecting() {
        listener = listener();

        listener.onApprovalRequestDecided(tenantId, ApprovalEntityType.EVALUATION_PANEL,
                panelId, ApprovalRequestStatus.REJECTED, actorUserId);

        // REJECT drives the DESTRUCTIVE reset-to-COLLECTING use case — NOT the
        // non-destructive reopen, and NOT approve.
        verify(resetPanelToCollecting).onRejected(tenantId, panelId, actorUserId);
        verify(reopenPanel, never()).onChangesRequested(tenantId, panelId, actorUserId);
        verify(approvePanel, never()).onApproved(tenantId, panelId, actorUserId);
    }

    @Test
    void changesRequestedNonDestructivelyReopensPanel() {
        listener = listener();

        listener.onApprovalRequestDecided(tenantId, ApprovalEntityType.EVALUATION_PANEL,
                panelId, ApprovalRequestStatus.CHANGES_REQUESTED, actorUserId);

        // CHANGES_REQUESTED stays on the NON-destructive reopen path — distinct
        // from REJECT; the destructive reset is never invoked.
        verify(reopenPanel).onChangesRequested(tenantId, panelId, actorUserId);
        verify(resetPanelToCollecting, never()).onRejected(tenantId, panelId, actorUserId);
        verify(approvePanel, never()).onApproved(tenantId, panelId, actorUserId);
    }

    @Test
    void approvedStillFlipsPanelApprovedUnchanged() {
        listener = listener();

        listener.onApprovalRequestDecided(tenantId, ApprovalEntityType.EVALUATION_PANEL,
                panelId, ApprovalRequestStatus.APPROVED, actorUserId);

        verify(approvePanel).onApproved(tenantId, panelId, actorUserId);
        verify(reopenPanel, never()).onChangesRequested(tenantId, panelId, actorUserId);
        verify(resetPanelToCollecting, never()).onRejected(tenantId, panelId, actorUserId);
    }

    @Test
    void pendingMidChainStepMutatesNothing() {
        listener = listener();

        listener.onApprovalRequestDecided(tenantId, ApprovalEntityType.EVALUATION_PANEL,
                panelId, ApprovalRequestStatus.PENDING, actorUserId);

        verifyNoInteractions(approvePanel, reopenPanel, resetPanelToCollecting);
    }

    @Test
    void nonPanelEntityTypeIsIgnoredForEveryStatus() {
        listener = listener();

        for (ApprovalRequestStatus s : ApprovalRequestStatus.values()) {
            listener.onApprovalRequestDecided(tenantId, ApprovalEntityType.EVALUATION,
                    panelId, s, actorUserId);
        }

        verifyNoInteractions(approvePanel, reopenPanel, resetPanelToCollecting);
    }
}
