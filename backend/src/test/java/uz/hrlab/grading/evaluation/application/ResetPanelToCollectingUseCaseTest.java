package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;
import uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus;
import uz.hrlab.grading.evaluation.domain.PanelStatusTransitionPolicy;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
 * {@link ResetPanelToCollectingUseCase} — the DESTRUCTIVE CEO-REJECT path. Proves
 * that on a SUBMITTED panel the listener-driven {@code onRejected}:
 * <ul>
 *   <li>DELETES every per-evaluator sheet + its scores through the REUSED
 *       {@link DeleteEvaluationUseCase#deleteForSystem} (no hand-rolled cascade);</li>
 *   <li>CLEARS the panel aggregates through the REUSED
 *       {@link ReopenPanelUseCase#clearPanelAggregates} (totals nulled,
 *       panel_factor_averages deleted);</li>
 *   <li>KEEPS the roster assignments (NOT deleted) but resets each non-withdrawn
 *       seat to ASSIGNED with evaluation_id nulled; WITHDRAWN seats untouched;</li>
 *   <li>flips status SUBMITTED → COLLECTING (allowed by the transition policy);</li>
 *   <li>audits EVALUATION_PANEL_RESET_TO_DRAFT;</li>
 *   <li>is fail-soft for a missing panel and a no-op for a non-SUBMITTED panel.</li>
 * </ul>
 * Uses the REAL {@link PanelStatusTransitionPolicy} (a pure domain object) to also
 * assert the SUBMITTED→COLLECTING transition is genuinely permitted.
 */
@ExtendWith(MockitoExtension.class)
class ResetPanelToCollectingUseCaseTest {

    @Mock PanelRepository panels;
    @Mock EvaluationRepository evaluations;
    @Mock PanelAssignmentRepository assignments;
    @Mock DeleteEvaluationUseCase deleteEvaluation;
    @Mock ReopenPanelUseCase reopenPanel;
    @Mock AuditService audit;

    final PanelStatusTransitionPolicy transitionPolicy = new PanelStatusTransitionPolicy();

    ResetPanelToCollectingUseCase useCase;

    UUID tenantId;
    UUID projectId;
    UUID panelId;
    UUID positionId;
    UUID versionId;
    UUID ceo;

    @BeforeEach
    void setUp() {
        useCase = new ResetPanelToCollectingUseCase(
                panels, evaluations, assignments, deleteEvaluation, reopenPanel,
                transitionPolicy, audit);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        panelId = UUID.randomUUID();
        positionId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        ceo = UUID.randomUUID();
    }

    @Test
    void rejectDeletesSheetsAndScoresClearsAveragesKeepsRosterFlipsToCollectingAndAudits() {
        EvaluationPanelJpaEntity panel = panelWithAverage(EvaluationPanelStatus.SUBMITTED);
        when(panels.findByIdAndTenantId(panelId, tenantId)).thenReturn(Optional.of(panel));

        EvaluationJpaEntity sheetA = sheet(EvaluationStatus.COMPLETE);
        EvaluationJpaEntity sheetB = sheet(EvaluationStatus.DRAFT);
        when(evaluations.findAllByTenantIdAndPanelId(tenantId, panelId))
                .thenReturn(List.of(sheetA, sheetB));

        PanelAssignmentJpaEntity active = assignment(PanelAssignmentStatus.COMPLETED,
                EvaluatorRole.HR_DIRECTOR, sheetA.getId());
        PanelAssignmentJpaEntity inProgress = assignment(PanelAssignmentStatus.IN_PROGRESS,
                EvaluatorRole.DEPARTMENT_DIRECTOR, sheetB.getId());
        PanelAssignmentJpaEntity withdrawn = assignment(PanelAssignmentStatus.WITHDRAWN,
                EvaluatorRole.EXTERNAL_EXPERT, UUID.randomUUID());
        when(assignments.findAllByTenantIdAndPanelId(tenantId, panelId))
                .thenReturn(List.of(active, inProgress, withdrawn));

        EvaluationPanel result = useCase.onRejected(tenantId, panelId, ceo);

        // 1) Every per-evaluator sheet + its scores deleted via the REUSED cascade.
        verify(deleteEvaluation).deleteForSystem(eq(tenantId), eq(ceo), eq(sheetA), any());
        verify(deleteEvaluation).deleteForSystem(eq(tenantId), eq(ceo), eq(sheetB), any());

        // 2) Aggregates cleared via the REUSED helper (totals nulled + averages deleted).
        verify(reopenPanel).clearPanelAggregates(tenantId, panel);

        // 3) Roster KEPT (never deleted) — non-withdrawn seats reset to ASSIGNED +
        //    evaluation_id nulled; WITHDRAWN seat left as history.
        verify(assignments, never()).deleteAllByTenantIdAndPanelId(any(), any());
        assertThat(active.getAssignmentStatus()).isEqualTo(PanelAssignmentStatus.ASSIGNED);
        assertThat(active.getEvaluationId()).isNull();
        assertThat(inProgress.getAssignmentStatus()).isEqualTo(PanelAssignmentStatus.ASSIGNED);
        assertThat(inProgress.getEvaluationId()).isNull();
        assertThat(withdrawn.getAssignmentStatus()).isEqualTo(PanelAssignmentStatus.WITHDRAWN);
        verify(assignments).save(active);
        verify(assignments).save(inProgress);
        verify(assignments, never()).save(withdrawn);

        // 4) Status SUBMITTED → COLLECTING, panel saved.
        assertThat(panel.getStatus()).isEqualTo(EvaluationPanelStatus.COLLECTING);
        assertThat(result.status()).isEqualTo(EvaluationPanelStatus.COLLECTING);
        verify(panels).save(panel);

        // 5) Audited as the destructive reset (the surviving record of the reject).
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().action())
                .isEqualTo(AuditAction.EVALUATION_PANEL_RESET_TO_DRAFT);
        assertThat(captor.getValue().entityId()).isEqualTo(panelId);
        assertThat(captor.getValue().entityType()).isEqualTo("EvaluationPanel");
    }

    @Test
    void missingPanelIsFailSoftNoException() {
        when(panels.findByIdAndTenantId(panelId, tenantId)).thenReturn(Optional.empty());

        assertThat(useCase.onRejected(tenantId, panelId, ceo)).isNull();

        verify(deleteEvaluation, never()).deleteForSystem(any(), any(), any(), any());
        verify(reopenPanel, never()).clearPanelAggregates(any(), any());
        verify(panels, never()).save(any());
        verify(audit, never()).record(any());
    }

    @ParameterizedTest
    @EnumSource(value = EvaluationPanelStatus.class,
            names = {"COLLECTING", "AWAITING_EVALUATIONS", "AVERAGED", "APPROVED", "LOCKED", "ARCHIVED"})
    void nonSubmittedPanelIsDefensiveNoop(EvaluationPanelStatus status) {
        // The transition policy only permits RESET_TO_COLLECTING from SUBMITTED.
        EvaluationPanelJpaEntity panel = panelWithAverage(status);
        when(panels.findByIdAndTenantId(panelId, tenantId)).thenReturn(Optional.of(panel));

        EvaluationPanel result = useCase.onRejected(tenantId, panelId, ceo);

        assertThat(result.status()).isEqualTo(status); // unchanged
        verify(deleteEvaluation, never()).deleteForSystem(any(), any(), any(), any());
        verify(reopenPanel, never()).clearPanelAggregates(any(), any());
        verify(panels, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void transitionPolicyAllowsSubmittedToCollectingOnly() {
        // SUBMITTED → COLLECTING (the reject reset) is permitted...
        assertThat(transitionPolicy.isAllowed(EvaluationPanelStatus.SUBMITTED,
                uz.hrlab.grading.evaluation.domain.PanelStatusTransition.RESET_TO_COLLECTING))
                .isTrue();
        // ...and the destructive reset is NOT permitted from any other source state.
        for (EvaluationPanelStatus s : EvaluationPanelStatus.values()) {
            if (s == EvaluationPanelStatus.SUBMITTED) continue;
            assertThat(transitionPolicy.isAllowed(s,
                    uz.hrlab.grading.evaluation.domain.PanelStatusTransition.RESET_TO_COLLECTING))
                    .as("RESET_TO_COLLECTING must be rejected from %s", s)
                    .isFalse();
        }
    }

    // --------------------------------------------------------------- helpers

    private EvaluationPanelJpaEntity panelWithAverage(EvaluationPanelStatus status) {
        EvaluationPanelJpaEntity panel = new EvaluationPanelJpaEntity(
                panelId, tenantId, projectId, positionId, versionId, status, 3);
        panel.setRawTotalScore(new BigDecimal("20.0000"));
        panel.setDisplayedTotalScore(new BigDecimal("20.00"));
        panel.setAveragedAt(OffsetDateTime.now());
        panel.setAveragedBy(UUID.randomUUID());
        return panel;
    }

    private EvaluationJpaEntity sheet(EvaluationStatus status) {
        EvaluationJpaEntity e = new EvaluationJpaEntity(
                UUID.randomUUID(), tenantId, projectId, positionId, versionId,
                UUID.randomUUID(), status);
        e.setPanelId(panelId);
        return e;
    }

    private PanelAssignmentJpaEntity assignment(PanelAssignmentStatus status,
                                                EvaluatorRole role, UUID evaluationId) {
        PanelAssignmentJpaEntity a = new PanelAssignmentJpaEntity(
                UUID.randomUUID(), tenantId, panelId, UUID.randomUUID(), role, status);
        a.setEvaluationId(evaluationId);
        return a;
    }
}
