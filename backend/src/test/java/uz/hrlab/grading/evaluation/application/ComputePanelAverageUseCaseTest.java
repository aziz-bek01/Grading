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
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.PanelAverageResult;
import uz.hrlab.grading.evaluation.domain.PanelAveragingService;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BE-8 — server-only averaging compute. Pins denominator integrity (REQ-AVG-3 —
 * only COMPLETE sheets contribute; the count is server-derived), idempotency
 * (stale per-factor rows cleared then re-written), the AWAITING -> AVERAGED
 * transition, and the redacted audit (count + total only). Uses the REAL
 * {@link PanelAveragingService} so the math is exercised end-to-end.
 */
@ExtendWith(MockitoExtension.class)
class ComputePanelAverageUseCaseTest {

    @Mock PanelRepository panels;
    @Mock EvaluationRepository evaluations;
    @Mock EvaluationScoreRepository scores;
    @Mock PanelFactorAverageRepository averages;
    @Mock AuditService audit;

    final PanelAveragingService averaging = new PanelAveragingService();
    ComputePanelAverageUseCase useCase;

    UUID tenantId;
    UUID projectId;
    UUID panelId;
    UUID actor;
    UUID factorA;
    UUID factorB;

    @BeforeEach
    void setUp() {
        useCase = new ComputePanelAverageUseCase(
                panels, evaluations, scores, averages, averaging, audit);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        panelId = UUID.randomUUID();
        actor = UUID.randomUUID();
        factorA = UUID.randomUUID();
        factorB = UUID.randomUUID();
    }

    @Test
    void panelNotFoundThrows() {
        when(panels.findByIdAndTenantId(panelId, tenantId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.compute(tenantId, panelId, actor))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("PANEL_NOT_FOUND");
    }

    @Test
    void zeroCompletedSheetsThrows() {
        stubPanel(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        // Both sheets are non-COMPLETE -> filtered out -> empty -> reject.
        when(evaluations.findAllByTenantIdAndPanelId(tenantId, panelId)).thenReturn(List.of(
                sheet(EvaluationStatus.INCOMPLETE), sheet(EvaluationStatus.DRAFT)));

        assertThatThrownBy(() -> useCase.compute(tenantId, panelId, actor))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("NO_COMPLETED_SHEETS");
    }

    @Test
    void onlyCompletedSheetsCountTowardTheDenominator() {
        EvaluationPanelJpaEntity panel = stubPanel(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        EvaluationJpaEntity s1 = sheet(EvaluationStatus.COMPLETE);
        EvaluationJpaEntity s2 = sheet(EvaluationStatus.COMPLETE);
        EvaluationJpaEntity incomplete = sheet(EvaluationStatus.INCOMPLETE); // must NOT count
        when(evaluations.findAllByTenantIdAndPanelId(tenantId, panelId))
                .thenReturn(List.of(s1, s2, incomplete));
        // s1: A=10, B=4 ; s2: A=20, B=6 -> means A=15.0000, B=5.0000, total 20.0000.
        stubScores(s1, 10, 4);
        stubScores(s2, 20, 6);

        PanelAverageResult result = useCase.compute(tenantId, panelId, actor);

        assertThat(result.evaluatorCount()).isEqualTo(2); // incomplete excluded
        assertThat(result.avgByFactor().get(factorA)).isEqualByComparingTo("15.0000");
        assertThat(result.avgByFactor().get(factorB)).isEqualByComparingTo("5.0000");
        assertThat(panel.getRawTotalScore()).isEqualByComparingTo("20.0000");
        assertThat(panel.getDisplayedTotalScore()).isEqualByComparingTo("20.00");
        assertThat(panel.getAveragedBy()).isEqualTo(actor);
        assertThat(panel.getAveragedAt()).isNotNull();
        assertThat(panel.getStatus()).isEqualTo(EvaluationPanelStatus.AVERAGED);
    }

    @Test
    void clearsStaleAveragesBeforePersistingFreshOnes_idempotentRecompute() {
        stubPanel(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        EvaluationJpaEntity s1 = sheet(EvaluationStatus.COMPLETE);
        EvaluationJpaEntity s2 = sheet(EvaluationStatus.COMPLETE);
        when(evaluations.findAllByTenantIdAndPanelId(tenantId, panelId)).thenReturn(List.of(s1, s2));
        stubScores(s1, 10, 4);
        stubScores(s2, 20, 6);

        useCase.compute(tenantId, panelId, actor);

        // Stale per-factor rows deleted first (idempotency), then one row per factor.
        verify(averages).deleteAllByTenantIdAndPanelId(tenantId, panelId);
        verify(averages, times(2)).save(any(PanelFactorAverageJpaEntity.class));
    }

    @Test
    void recomputeIsDeterministicAcrossInvocations() {
        stubPanel(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        EvaluationJpaEntity s1 = sheet(EvaluationStatus.COMPLETE);
        EvaluationJpaEntity s2 = sheet(EvaluationStatus.COMPLETE);
        EvaluationJpaEntity s3 = sheet(EvaluationStatus.COMPLETE);
        when(evaluations.findAllByTenantIdAndPanelId(tenantId, panelId))
                .thenReturn(List.of(s1, s2, s3));
        stubScores(s1, 10, 1);
        stubScores(s2, 10, 1);
        stubScores(s3, 11, 2); // A mean = 31/3 = 10.3333 ; B mean = 4/3 = 1.3333

        PanelAverageResult first = useCase.compute(tenantId, panelId, actor);
        PanelAverageResult second = useCase.compute(tenantId, panelId, actor);

        assertThat(first.rawTotal()).isEqualByComparingTo(second.rawTotal());
        assertThat(first.avgByFactor().get(factorA)).isEqualByComparingTo("10.3333");
        assertThat(first.avgByFactor().get(factorB)).isEqualByComparingTo("1.3333");
    }

    @Test
    void auditRecordsCountAndTotalOnlyRedactingComments() {
        stubPanel(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        EvaluationJpaEntity s1 = sheet(EvaluationStatus.COMPLETE);
        EvaluationJpaEntity s2 = sheet(EvaluationStatus.COMPLETE);
        when(evaluations.findAllByTenantIdAndPanelId(tenantId, panelId)).thenReturn(List.of(s1, s2));
        stubScores(s1, 10, 4);
        stubScores(s2, 20, 6);

        useCase.compute(tenantId, panelId, actor);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        AuditEvent ev = captor.getValue();
        assertThat(ev.action()).isEqualTo(AuditAction.EVALUATION_PANEL_AVERAGED);
        assertThat(ev.reason()).contains("evaluatorCount=2").contains("rawTotal=");
        // No per-evaluator comments in the audit reason (REQ-AUD-3).
        assertThat(ev.reason()).doesNotContain("comment");
    }

    // --------------------------------------------------------------- helpers

    private EvaluationPanelJpaEntity stubPanel(EvaluationPanelStatus status) {
        EvaluationPanelJpaEntity panel = new EvaluationPanelJpaEntity(
                panelId, tenantId, projectId, UUID.randomUUID(), UUID.randomUUID(), status, 3);
        when(panels.findByIdAndTenantId(panelId, tenantId)).thenReturn(Optional.of(panel));
        return panel;
    }

    private EvaluationJpaEntity sheet(EvaluationStatus status) {
        EvaluationJpaEntity e = new EvaluationJpaEntity(
                UUID.randomUUID(), tenantId, projectId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), status);
        e.setPanelId(panelId);
        return e;
    }

    private void stubScores(EvaluationJpaEntity sheet, int a, int b) {
        when(scores.findAllByTenantIdAndEvaluationId(tenantId, sheet.getId())).thenReturn(List.of(
                new EvaluationScoreJpaEntity(UUID.randomUUID(), tenantId, sheet.getId(),
                        factorA, UUID.randomUUID(), new BigDecimal(a)),
                new EvaluationScoreJpaEntity(UUID.randomUUID(), tenantId, sheet.getId(),
                        factorB, UUID.randomUUID(), new BigDecimal(b))));
    }
}
