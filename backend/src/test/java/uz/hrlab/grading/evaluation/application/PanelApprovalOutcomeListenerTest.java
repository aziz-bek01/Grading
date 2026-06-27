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
 * CEO inline sign-off — Task A. A REJECT of an {@code EVALUATION_PANEL} approval
 * must NOT orphan the panel in SUBMITTED: the listener's REJECTED branch reopens
 * the panel through the SAME {@link ReopenPanelUseCase#onChangesRequested} path as
 * CHANGES_REQUESTED (back to AWAITING_EVALUATIONS, stale averages cleared, sheets
 * preserved). The actual status/average mutation + audit are proven by
 * {@link ReopenPanelUseCaseTest}; here we prove the WIRING — REJECTED drives the
 * reopen use case, while APPROVED and CHANGES_REQUESTED are unchanged and a
 * non-panel entity type is ignored.
 */
@ExtendWith(MockitoExtension.class)
class PanelApprovalOutcomeListenerTest {

    @Mock ApprovePanelUseCase approvePanel;
    @Mock ReopenPanelUseCase reopenPanel;

    PanelApprovalOutcomeListener listener;

    final UUID tenantId = UUID.randomUUID();
    final UUID panelId = UUID.randomUUID();
    final UUID actorUserId = UUID.randomUUID();

    private PanelApprovalOutcomeListener listener() {
        return new PanelApprovalOutcomeListener(approvePanel, reopenPanel);
    }

    @Test
    void rejectedReopensPanelViaSameReopenPathAsChangesRequested() {
        listener = listener();

        listener.onApprovalRequestDecided(tenantId, ApprovalEntityType.EVALUATION_PANEL,
                panelId, ApprovalRequestStatus.REJECTED, actorUserId);

        // The panel is sent back to a re-editable state through the reused reopen
        // use case — NOT left orphaned in SUBMITTED, and the approve path is untouched.
        verify(reopenPanel).onChangesRequested(tenantId, panelId, actorUserId);
        verify(approvePanel, never()).onApproved(tenantId, panelId, actorUserId);
    }

    @Test
    void changesRequestedStillReopensPanelUnchanged() {
        listener = listener();

        listener.onApprovalRequestDecided(tenantId, ApprovalEntityType.EVALUATION_PANEL,
                panelId, ApprovalRequestStatus.CHANGES_REQUESTED, actorUserId);

        verify(reopenPanel).onChangesRequested(tenantId, panelId, actorUserId);
        verify(approvePanel, never()).onApproved(tenantId, panelId, actorUserId);
    }

    @Test
    void approvedStillFlipsPanelApprovedUnchanged() {
        listener = listener();

        listener.onApprovalRequestDecided(tenantId, ApprovalEntityType.EVALUATION_PANEL,
                panelId, ApprovalRequestStatus.APPROVED, actorUserId);

        verify(approvePanel).onApproved(tenantId, panelId, actorUserId);
        verify(reopenPanel, never()).onChangesRequested(tenantId, panelId, actorUserId);
    }

    @Test
    void pendingMidChainStepMutatesNothing() {
        listener = listener();

        listener.onApprovalRequestDecided(tenantId, ApprovalEntityType.EVALUATION_PANEL,
                panelId, ApprovalRequestStatus.PENDING, actorUserId);

        verifyNoInteractions(approvePanel, reopenPanel);
    }

    @Test
    void nonPanelEntityTypeIsIgnoredForEveryStatus() {
        listener = listener();

        for (ApprovalRequestStatus s : ApprovalRequestStatus.values()) {
            listener.onApprovalRequestDecided(tenantId, ApprovalEntityType.EVALUATION,
                    panelId, s, actorUserId);
        }

        verifyNoInteractions(approvePanel, reopenPanel);
    }
}
