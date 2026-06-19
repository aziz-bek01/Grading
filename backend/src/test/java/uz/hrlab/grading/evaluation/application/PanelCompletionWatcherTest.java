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
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;
import uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;

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
 * BE-7 — completion watcher. Pins: seat sync on each recompute, the COMPLETE-edge
 * audit, and the single-predicate "all active complete" gate that triggers
 * averaging — including the edge where one active seat is still incomplete (no
 * trigger) and where a WITHDRAWN seat is excluded from the gate so the panel can
 * still complete on the remaining active seats.
 */
@ExtendWith(MockitoExtension.class)
class PanelCompletionWatcherTest {

    @Mock PanelRepository panels;
    @Mock PanelAssignmentRepository assignments;
    @Mock ComputePanelAverageUseCase computeAverage;
    @Mock SubmitPanelToCeoUseCase submitToCeo;
    @Mock AuditService audit;

    PanelCompletionWatcher watcher;

    UUID tenantId;
    UUID projectId;
    UUID panelId;
    UUID actor;

    @BeforeEach
    void setUp() {
        watcher = new PanelCompletionWatcher(
                panels, assignments, computeAverage, submitToCeo, audit);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        panelId = UUID.randomUUID();
        actor = UUID.randomUUID();
    }

    @Test
    void panellessEvaluationIsIgnored() {
        EvaluationJpaEntity eval = evaluation(EvaluationStatus.COMPLETE, null);
        watcher.onEvaluationStatusRecomputed(eval, actor);
        verify(panels, never()).findByIdAndTenantId(any(), any());
        verify(computeAverage, never()).compute(any(), any(), any());
    }

    @Test
    void panelNotAwaitingEvaluationsIsIgnored() {
        EvaluationJpaEntity eval = evaluation(EvaluationStatus.COMPLETE, panelId);
        stubPanel(EvaluationPanelStatus.AVERAGED);
        watcher.onEvaluationStatusRecomputed(eval, actor);
        verify(assignments, never()).findByTenantIdAndEvaluationId(any(), any());
        verify(computeAverage, never()).compute(any(), any(), any());
    }

    @Test
    void incompleteRecomputeSyncsSeatToInProgressWithoutTriggeringAverage() {
        EvaluationJpaEntity eval = evaluation(EvaluationStatus.INCOMPLETE, panelId);
        stubPanel(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        PanelAssignmentJpaEntity seat = seat(eval.getId(), PanelAssignmentStatus.ASSIGNED);
        when(assignments.findByTenantIdAndEvaluationId(tenantId, eval.getId()))
                .thenReturn(Optional.of(seat));

        watcher.onEvaluationStatusRecomputed(eval, actor);

        assertThat(seat.getAssignmentStatus()).isEqualTo(PanelAssignmentStatus.IN_PROGRESS);
        verify(audit, never()).record(any()); // no COMPLETE-edge audit
        verify(computeAverage, never()).compute(any(), any(), any());
    }

    @Test
    void lastCompletionTriggersAverageAndEmitsCompletedAudit() {
        EvaluationJpaEntity eval = evaluation(EvaluationStatus.COMPLETE, panelId);
        stubPanel(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        PanelAssignmentJpaEntity me = seat(eval.getId(), PanelAssignmentStatus.IN_PROGRESS);
        when(assignments.findByTenantIdAndEvaluationId(tenantId, eval.getId()))
                .thenReturn(Optional.of(me));
        // Roster: my seat will flip to COMPLETED; the other two are already COMPLETED.
        when(assignments.findAllByTenantIdAndPanelId(tenantId, panelId)).thenReturn(List.of(
                me,
                seat(UUID.randomUUID(), PanelAssignmentStatus.COMPLETED),
                seat(UUID.randomUUID(), PanelAssignmentStatus.COMPLETED)));

        watcher.onEvaluationStatusRecomputed(eval, actor);

        assertThat(me.getAssignmentStatus()).isEqualTo(PanelAssignmentStatus.COMPLETED);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().action())
                .isEqualTo(AuditAction.EVALUATION_PANEL_EVALUATOR_COMPLETED);
        verify(computeAverage).compute(eq(tenantId), eq(panelId), eq(actor));
        // Consolidated panel flow — auto-submit the averaged panel to the CEO.
        verify(submitToCeo).submitSystem(eq(panelId), eq(actor));
    }

    @Test
    void averagingFollowedByAutoSubmitIsFailSoftWhenSubmitThrows() {
        EvaluationJpaEntity eval = evaluation(EvaluationStatus.COMPLETE, panelId);
        stubPanel(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        PanelAssignmentJpaEntity me = seat(eval.getId(), PanelAssignmentStatus.IN_PROGRESS);
        when(assignments.findByTenantIdAndEvaluationId(tenantId, eval.getId()))
                .thenReturn(Optional.of(me));
        when(assignments.findAllByTenantIdAndPanelId(tenantId, panelId)).thenReturn(List.of(
                me,
                seat(UUID.randomUUID(), PanelAssignmentStatus.COMPLETED),
                seat(UUID.randomUUID(), PanelAssignmentStatus.COMPLETED)));
        // Averaging succeeded; the auto CEO submit blows up for a non-fatal reason.
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(submitToCeo).submitSystem(eq(panelId), eq(actor));

        // Must NOT propagate — averaging already persisted; submit is fail-soft.
        watcher.onEvaluationStatusRecomputed(eval, actor);

        verify(computeAverage).compute(eq(tenantId), eq(panelId), eq(actor));
        verify(submitToCeo).submitSystem(eq(panelId), eq(actor));
    }

    @Test
    void averagingIsNotTriggeredWhileAnotherActiveSeatIsStillIncomplete() {
        EvaluationJpaEntity eval = evaluation(EvaluationStatus.COMPLETE, panelId);
        stubPanel(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        PanelAssignmentJpaEntity me = seat(eval.getId(), PanelAssignmentStatus.IN_PROGRESS);
        when(assignments.findByTenantIdAndEvaluationId(tenantId, eval.getId()))
                .thenReturn(Optional.of(me));
        when(assignments.findAllByTenantIdAndPanelId(tenantId, panelId)).thenReturn(List.of(
                me,
                seat(UUID.randomUUID(), PanelAssignmentStatus.COMPLETED),
                seat(UUID.randomUUID(), PanelAssignmentStatus.IN_PROGRESS))); // not done

        watcher.onEvaluationStatusRecomputed(eval, actor);

        assertThat(me.getAssignmentStatus()).isEqualTo(PanelAssignmentStatus.COMPLETED);
        verify(computeAverage, never()).compute(any(), any(), any());
        verify(submitToCeo, never()).submitSystem(any(), any());
    }

    @Test
    void withdrawnSeatIsExcludedFromTheAllCompleteGate() {
        EvaluationJpaEntity eval = evaluation(EvaluationStatus.COMPLETE, panelId);
        stubPanel(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        PanelAssignmentJpaEntity me = seat(eval.getId(), PanelAssignmentStatus.IN_PROGRESS);
        when(assignments.findByTenantIdAndEvaluationId(tenantId, eval.getId()))
                .thenReturn(Optional.of(me));
        // Two active COMPLETED + my completion; a WITHDRAWN seat must NOT block.
        when(assignments.findAllByTenantIdAndPanelId(tenantId, panelId)).thenReturn(List.of(
                me,
                seat(UUID.randomUUID(), PanelAssignmentStatus.COMPLETED),
                seat(UUID.randomUUID(), PanelAssignmentStatus.WITHDRAWN)));

        watcher.onEvaluationStatusRecomputed(eval, actor);

        verify(computeAverage).compute(eq(tenantId), eq(panelId), eq(actor));
        verify(submitToCeo).submitSystem(eq(panelId), eq(actor));
    }

    @Test
    void recomputeForWithdrawnSeatDoesNotSyncOrTrigger() {
        EvaluationJpaEntity eval = evaluation(EvaluationStatus.COMPLETE, panelId);
        stubPanel(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        PanelAssignmentJpaEntity withdrawn = seat(eval.getId(), PanelAssignmentStatus.WITHDRAWN);
        when(assignments.findByTenantIdAndEvaluationId(tenantId, eval.getId()))
                .thenReturn(Optional.of(withdrawn));

        watcher.onEvaluationStatusRecomputed(eval, actor);

        verify(assignments, never()).save(any());
        verify(computeAverage, never()).compute(any(), any(), any());
    }

    // --------------------------------------------------------------- helpers

    private void stubPanel(EvaluationPanelStatus status) {
        EvaluationPanelJpaEntity panel = new EvaluationPanelJpaEntity(
                panelId, tenantId, projectId, UUID.randomUUID(), UUID.randomUUID(), status, 3);
        when(panels.findByIdAndTenantId(panelId, tenantId)).thenReturn(Optional.of(panel));
    }

    private EvaluationJpaEntity evaluation(EvaluationStatus status, UUID panel) {
        EvaluationJpaEntity e = new EvaluationJpaEntity(
                UUID.randomUUID(), tenantId, projectId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), status);
        e.setPanelId(panel);
        return e;
    }

    private PanelAssignmentJpaEntity seat(UUID evaluationId, PanelAssignmentStatus status) {
        PanelAssignmentJpaEntity a = new PanelAssignmentJpaEntity(
                UUID.randomUUID(), tenantId, panelId, UUID.randomUUID(),
                EvaluatorRole.ADDITIONAL, status);
        a.setEvaluationId(evaluationId);
        return a;
    }
}
