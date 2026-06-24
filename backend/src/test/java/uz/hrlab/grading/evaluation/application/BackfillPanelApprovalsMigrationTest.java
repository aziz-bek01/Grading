package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.approval.application.CancelApprovalRequestUseCase;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequest;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestRepository;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One-time CEO-approval reconciliation migration (Defect: CEO inbox shows stale
 * per-EVALUATION cards / panels stuck SUBMITTED with no panel approval). Pins:
 * <ul>
 *   <li>branch (1) cancels a PENDING EVALUATION approval ONLY when its evaluation
 *       is now panel-wrapped (panel_id != null), via the shared
 *       {@code CancelApprovalRequestUseCase.cancelSystem};</li>
 *   <li>a PENDING EVALUATION approval on a NON-panel evaluation is left untouched;</li>
 *   <li>branch (2) opens the missing CEO approval for a SUBMITTED panel via the
 *       shared {@code CeoPanelApprovalOpener} (same collaborator the live submit
 *       flow uses — no duplication);</li>
 *   <li>idempotency: a no-op cancel ({@code null}) / opener ({@code null}) is not
 *       counted, so a re-run does nothing;</li>
 *   <li>anti-BOLA: a tenant-context mismatch is rejected.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BackfillPanelApprovalsMigrationTest {

    @Mock ApprovalRequestRepository approvalRequests;
    @Mock EvaluationRepository evaluations;
    @Mock PanelRepository panels;
    @Mock CancelApprovalRequestUseCase cancelApprovalRequest;
    @Mock CeoPanelApprovalOpener ceoApprovalOpener;
    @Mock PanelCompletionChecker completionChecker;
    @Mock ComputePanelAverageUseCase computeAverage;
    @Mock SubmitPanelToCeoUseCase submitToCeo;
    @Mock PanelFactorAverageRepository factorAverages;

    BackfillPanelApprovalsMigration migration;
    UUID tenantId;

    @BeforeEach
    void setUp() {
        migration = new BackfillPanelApprovalsMigration(
                approvalRequests, evaluations, panels, cancelApprovalRequest, ceoApprovalOpener,
                completionChecker, computeAverage, submitToCeo, factorAverages);
        tenantId = UUID.randomUUID();
        setContext(tenantId);
        // The advance sweep queries panels in THREE statuses (AWAITING/AVERAGED/
        // SUBMITTED). Default every status to empty (lenient); each test overrides
        // only the status it exercises.
        lenient().when(panels.findAllByTenantIdAndStatus(eq(tenantId), any()))
                .thenReturn(List.of());
        // Default: a SUBMITTED panel ALREADY has its averages (the common case), so
        // the branch-(3) repair is skipped. The repair test overrides this to empty.
        lenient().when(factorAverages.findAllByTenantIdAndPanelId(any(), any()))
                .thenReturn(List.of(mock(PanelFactorAverageJpaEntity.class)));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void cancelsLegacyApprovalWhenEvaluationIsPanelWrapped() {
        UUID evalId = UUID.randomUUID();
        UUID reqId = UUID.randomUUID();
        ApprovalRequestJpaEntity req = mock(ApprovalRequestJpaEntity.class);
        when(req.getEntityId()).thenReturn(evalId);
        when(req.getId()).thenReturn(reqId);
        when(approvalRequests.findAllByTenantIdAndEntityTypeAndCurrentStatus(
                tenantId, ApprovalEntityType.EVALUATION, ApprovalRequestStatus.PENDING))
                .thenReturn(List.of(req));
        EvaluationJpaEntity eval = mock(EvaluationJpaEntity.class);
        when(eval.getPanelId()).thenReturn(UUID.randomUUID()); // panel-wrapped
        when(evaluations.findByIdAndTenantId(evalId, tenantId)).thenReturn(Optional.of(eval));
        when(cancelApprovalRequest.cancelSystem(reqId, BackfillPanelApprovalsMigration.CANCEL_REASON))
                .thenReturn(mock(ApprovalRequest.class));

        BackfillPanelApprovalsMigration.Result result = migration.runForTenant(tenantId);

        assertThat(result.cancelledLegacyApprovals()).isEqualTo(1);
        assertThat(result.openedPanelApprovals()).isZero();
        verify(cancelApprovalRequest)
                .cancelSystem(reqId, BackfillPanelApprovalsMigration.CANCEL_REASON);
    }

    @Test
    void leavesNonPanelEvaluationApprovalUntouched() {
        UUID evalId = UUID.randomUUID();
        ApprovalRequestJpaEntity req = mock(ApprovalRequestJpaEntity.class);
        when(req.getEntityId()).thenReturn(evalId);
        when(approvalRequests.findAllByTenantIdAndEntityTypeAndCurrentStatus(
                tenantId, ApprovalEntityType.EVALUATION, ApprovalRequestStatus.PENDING))
                .thenReturn(List.of(req));
        EvaluationJpaEntity eval = mock(EvaluationJpaEntity.class);
        when(eval.getPanelId()).thenReturn(null); // genuine single-evaluator — keep it
        when(evaluations.findByIdAndTenantId(evalId, tenantId)).thenReturn(Optional.of(eval));

        BackfillPanelApprovalsMigration.Result result = migration.runForTenant(tenantId);

        assertThat(result.cancelledLegacyApprovals()).isZero();
        verify(cancelApprovalRequest, never()).cancelSystem(any(), any());
    }

    @Test
    void opensMissingPanelApprovalForSubmittedPanel() {
        EvaluationPanelJpaEntity panel = mock(EvaluationPanelJpaEntity.class);
        when(panels.findAllByTenantIdAndStatus(tenantId, EvaluationPanelStatus.SUBMITTED))
                .thenReturn(List.of(panel));
        when(ceoApprovalOpener.openIfAbsent(tenantId, panel)).thenReturn(UUID.randomUUID());

        BackfillPanelApprovalsMigration.Result result = migration.runForTenant(tenantId);

        assertThat(result.openedPanelApprovals()).isEqualTo(1);
        verify(ceoApprovalOpener).openIfAbsent(tenantId, panel);
    }

    @Test
    void idempotentWhenOpenerReportsExistingRequest() {
        EvaluationPanelJpaEntity panel = mock(EvaluationPanelJpaEntity.class);
        when(panels.findAllByTenantIdAndStatus(tenantId, EvaluationPanelStatus.SUBMITTED))
                .thenReturn(List.of(panel));
        when(ceoApprovalOpener.openIfAbsent(tenantId, panel)).thenReturn(null); // already pending

        BackfillPanelApprovalsMigration.Result result = migration.runForTenant(tenantId);

        assertThat(result.openedPanelApprovals()).isZero();
    }

    @Test
    void idempotentWhenLegacyApprovalAlreadyCancelled() {
        UUID evalId = UUID.randomUUID();
        UUID reqId = UUID.randomUUID();
        ApprovalRequestJpaEntity req = mock(ApprovalRequestJpaEntity.class);
        when(req.getEntityId()).thenReturn(evalId);
        when(req.getId()).thenReturn(reqId);
        when(approvalRequests.findAllByTenantIdAndEntityTypeAndCurrentStatus(
                tenantId, ApprovalEntityType.EVALUATION, ApprovalRequestStatus.PENDING))
                .thenReturn(List.of(req));
        EvaluationJpaEntity eval = mock(EvaluationJpaEntity.class);
        when(eval.getPanelId()).thenReturn(UUID.randomUUID());
        when(evaluations.findByIdAndTenantId(evalId, tenantId)).thenReturn(Optional.of(eval));
        // cancelSystem returns null when the request is no longer PENDING (no-op).
        when(cancelApprovalRequest.cancelSystem(reqId, BackfillPanelApprovalsMigration.CANCEL_REASON))
                .thenReturn(null);

        BackfillPanelApprovalsMigration.Result result = migration.runForTenant(tenantId);

        assertThat(result.cancelledLegacyApprovals()).isZero();
    }

    @Test
    void tenantContextMismatchIsRejected() {
        UUID otherTenant = UUID.randomUUID();
        assertThatThrownBy(() -> migration.runForTenant(otherTenant))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant context mismatch");
    }

    @Test
    void advancesFullyCompleteAwaitingPanel() {
        // Regression: a panel whose roster is fully complete but that never averaged
        // (the watcher only fires on a score recompute) must be averaged + submitted
        // so the CEO sees it.
        UUID panelId = UUID.randomUUID();
        EvaluationPanelJpaEntity panel = mock(EvaluationPanelJpaEntity.class);
        when(panel.getId()).thenReturn(panelId);
        when(panels.findAllByTenantIdAndStatus(tenantId, EvaluationPanelStatus.AWAITING_EVALUATIONS))
                .thenReturn(List.of(panel));
        when(completionChecker.allActiveComplete(tenantId, panelId)).thenReturn(true);

        BackfillPanelApprovalsMigration.Result result = migration.runForTenant(tenantId);

        assertThat(result.openedPanelApprovals()).isEqualTo(1);
        verify(computeAverage).compute(tenantId, panelId, null);
        verify(submitToCeo).submitSystem(panelId, null);
    }

    @Test
    void skipsIncompleteAwaitingPanel() {
        UUID panelId = UUID.randomUUID();
        EvaluationPanelJpaEntity panel = mock(EvaluationPanelJpaEntity.class);
        when(panel.getId()).thenReturn(panelId);
        when(panels.findAllByTenantIdAndStatus(tenantId, EvaluationPanelStatus.AWAITING_EVALUATIONS))
                .thenReturn(List.of(panel));
        when(completionChecker.allActiveComplete(tenantId, panelId)).thenReturn(false);

        BackfillPanelApprovalsMigration.Result result = migration.runForTenant(tenantId);

        assertThat(result.openedPanelApprovals()).isZero();
        verify(computeAverage, never()).compute(any(), any(), any());
        verify(submitToCeo, never()).submitSystem(any(), any());
    }

    @Test
    void submitsStuckAveragedPanel() {
        // Regression: a panel averaged but never submitted (auto-submit fail-soft
        // swallow) must be submitted so the CEO approval opens.
        UUID panelId = UUID.randomUUID();
        EvaluationPanelJpaEntity panel = mock(EvaluationPanelJpaEntity.class);
        when(panel.getId()).thenReturn(panelId);
        when(panels.findAllByTenantIdAndStatus(tenantId, EvaluationPanelStatus.AVERAGED))
                .thenReturn(List.of(panel));

        BackfillPanelApprovalsMigration.Result result = migration.runForTenant(tenantId);

        assertThat(result.openedPanelApprovals()).isEqualTo(1);
        verify(submitToCeo).submitSystem(panelId, null);
        verify(computeAverage, never()).compute(any(), any(), any()); // already averaged
    }

    @Test
    void repairsSubmittedPanelMissingAverages() {
        // Regression: a 036-backfilled SUBMITTED wrapper that never got
        // panel_factor_averages (036 seeded averages only for APPROVED/LOCKED) reaches
        // the CEO with an empty average. The sweep must re-derive it via compute()
        // (which keeps the panel SUBMITTED) before opening the approval, so the CEO
        // sees a real averaged result instead of "0 evaluator(s) / —".
        UUID panelId = UUID.randomUUID();
        EvaluationPanelJpaEntity panel = mock(EvaluationPanelJpaEntity.class);
        when(panel.getId()).thenReturn(panelId);
        when(panels.findAllByTenantIdAndStatus(tenantId, EvaluationPanelStatus.SUBMITTED))
                .thenReturn(List.of(panel));
        when(factorAverages.findAllByTenantIdAndPanelId(tenantId, panelId))
                .thenReturn(List.of()); // no averages yet → repair
        when(ceoApprovalOpener.openIfAbsent(tenantId, panel)).thenReturn(UUID.randomUUID());

        BackfillPanelApprovalsMigration.Result result = migration.runForTenant(tenantId);

        verify(computeAverage).compute(tenantId, panelId, null);
        verify(ceoApprovalOpener).openIfAbsent(tenantId, panel);
        assertThat(result.openedPanelApprovals()).isEqualTo(1);
    }

    // --------------------------------------------------------------- helpers

    private void setContext(UUID tenant) {
        TenantContextHolder.set(new TenantContext(
                null, tenant, Set.of(), Set.of(), Set.of(), Set.of(), false, null));
    }
}
