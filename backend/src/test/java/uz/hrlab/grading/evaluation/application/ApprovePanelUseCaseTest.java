package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BE-10 — panel CEO-approval coupling. On the last EVALUATION_PANEL step approving,
 * the panel flips SUBMITTED -> APPROVED, the grade is assigned from the AVERAGED
 * total via the REUSED {@link EvaluationGradeAssignmentService#assignToPanel}
 * (no new grade logic — REQ-CEO-3), and the contributing COMPLETE sheets are
 * locked (immutability symmetry — REQ-AVG-4). Fail-soft for a stale/missing panel
 * so the approval decision is never broken.
 */
@ExtendWith(MockitoExtension.class)
class ApprovePanelUseCaseTest {

    @Mock PanelRepository panels;
    @Mock EvaluationRepository evaluations;
    @Mock EvaluationGradeAssignmentService gradeAssignment;
    @Mock AuditService audit;

    ApprovePanelUseCase useCase;

    UUID tenantId;
    UUID projectId;
    UUID panelId;
    UUID ceo;

    @BeforeEach
    void setUp() {
        useCase = new ApprovePanelUseCase(panels, evaluations, gradeAssignment, audit);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        panelId = UUID.randomUUID();
        ceo = UUID.randomUUID();
    }

    @Test
    void missingPanelIsFailSoftNoException() {
        when(panels.findByIdAndTenantId(panelId, tenantId)).thenReturn(Optional.empty());
        assertThat(useCase.onApproved(tenantId, panelId, ceo)).isNull();
        verify(gradeAssignment, never()).assignToPanel(any(), any());
    }

    @Test
    void nonSubmittedPanelIsIgnored() {
        EvaluationPanelJpaEntity panel = panel(EvaluationPanelStatus.AVERAGED);
        when(panels.findByIdAndTenantId(panelId, tenantId)).thenReturn(Optional.of(panel));

        useCase.onApproved(tenantId, panelId, ceo);

        assertThat(panel.getStatus()).isEqualTo(EvaluationPanelStatus.AVERAGED); // unchanged
        verify(gradeAssignment, never()).assignToPanel(any(), any());
        verify(panels, never()).save(any());
    }

    @Test
    void approvesAssignsGradeFromAveragedTotalAndLocksSheets() {
        EvaluationPanelJpaEntity panel = panel(EvaluationPanelStatus.SUBMITTED);
        panel.setRawTotalScore(new BigDecimal("20.0000"));
        panel.setDisplayedTotalScore(new BigDecimal("20.00"));
        when(panels.findByIdAndTenantId(panelId, tenantId)).thenReturn(Optional.of(panel));

        EvaluationJpaEntity complete = sheet(EvaluationStatus.COMPLETE);
        EvaluationJpaEntity alsoComplete = sheet(EvaluationStatus.COMPLETE);
        when(evaluations.findAllByTenantIdAndPanelId(tenantId, panelId))
                .thenReturn(List.of(complete, alsoComplete));

        useCase.onApproved(tenantId, panelId, ceo);

        assertThat(panel.getStatus()).isEqualTo(EvaluationPanelStatus.APPROVED);
        assertThat(panel.getApprovedBy()).isEqualTo(ceo);
        assertThat(panel.getApprovedAt()).isNotNull();
        // Reuse of the existing grade-band lookup against the panel's averaged score.
        verify(gradeAssignment).assignToPanel(eq(panel), eq(ceo));
        verify(panels).save(panel);
        // Contributing COMPLETE sheets locked (immutability symmetry).
        assertThat(complete.getStatus()).isEqualTo(EvaluationStatus.LOCKED);
        assertThat(alsoComplete.getStatus()).isEqualTo(EvaluationStatus.LOCKED);

        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(ev.capture());
        assertThat(ev.getValue().action()).isEqualTo(AuditAction.EVALUATION_PANEL_APPROVED);
    }

    @Test
    void locksSubmittedSheetsToo_notOnlyComplete() {
        // Regression: a panel evaluator who hit the per-sheet "Submit" (→ SUBMITTED)
        // must still be frozen when the CEO approves the panel — otherwise their
        // sheet stays editable after approval (immutability gap). An already-LOCKED
        // sheet is left untouched.
        EvaluationPanelJpaEntity panel = panel(EvaluationPanelStatus.SUBMITTED);
        panel.setRawTotalScore(new BigDecimal("20.0000"));
        when(panels.findByIdAndTenantId(panelId, tenantId)).thenReturn(Optional.of(panel));

        EvaluationJpaEntity submitted = sheet(EvaluationStatus.SUBMITTED);
        EvaluationJpaEntity complete = sheet(EvaluationStatus.COMPLETE);
        EvaluationJpaEntity alreadyLocked = sheet(EvaluationStatus.LOCKED);
        when(evaluations.findAllByTenantIdAndPanelId(tenantId, panelId))
                .thenReturn(List.of(submitted, complete, alreadyLocked));

        useCase.onApproved(tenantId, panelId, ceo);

        assertThat(submitted.getStatus()).isEqualTo(EvaluationStatus.LOCKED);
        assertThat(complete.getStatus()).isEqualTo(EvaluationStatus.LOCKED);
        assertThat(submitted.getLockedBy()).isEqualTo(ceo);
        // Already-LOCKED sheet is not re-saved.
        verify(evaluations, never()).save(alreadyLocked);
    }

    @Test
    void doesNotLockNonCompleteSheets() {
        EvaluationPanelJpaEntity panel = panel(EvaluationPanelStatus.SUBMITTED);
        when(panels.findByIdAndTenantId(panelId, tenantId)).thenReturn(Optional.of(panel));
        EvaluationJpaEntity incomplete = sheet(EvaluationStatus.INCOMPLETE);
        when(evaluations.findAllByTenantIdAndPanelId(tenantId, panelId))
                .thenReturn(List.of(incomplete));

        useCase.onApproved(tenantId, panelId, ceo);

        assertThat(incomplete.getStatus()).isEqualTo(EvaluationStatus.INCOMPLETE);
        verify(evaluations, never()).save(incomplete);
    }

    // --------------------------------------------------------------- helpers

    private EvaluationPanelJpaEntity panel(EvaluationPanelStatus status) {
        return new EvaluationPanelJpaEntity(
                panelId, tenantId, projectId, UUID.randomUUID(), UUID.randomUUID(), status, 3);
    }

    private EvaluationJpaEntity sheet(EvaluationStatus status) {
        EvaluationJpaEntity e = new EvaluationJpaEntity(
                UUID.randomUUID(), tenantId, projectId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), status);
        e.setPanelId(panelId);
        return e;
    }
}
