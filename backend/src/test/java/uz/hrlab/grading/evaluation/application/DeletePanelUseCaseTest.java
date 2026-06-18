package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Defect-2 BE — {@link DeletePanelUseCase}. Mirrors {@code DeleteEvaluationUseCaseTest}.
 * Asserts:
 * <ul>
 *   <li>a {@code COLLECTING} panel deletes: roster seats removed FIRST, parent
 *       removed via tenant-scoped {@code deleteByIdAndTenantId}, EVALUATION_PANEL_DELETED
 *       audited — and the active-panel uniqueness slot is freed for a re-create;</li>
 *   <li>every non-COLLECTING status → 400 {@code PANEL_NOT_DELETABLE} (nothing
 *       deleted or audited);</li>
 *   <li>missing/short reason → 400 before any load;</li>
 *   <li>missing EVALUATION_PANEL_MANAGE → 403;</li>
 *   <li>cross-tenant id → 404 from the loader (no delete).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DeletePanelUseCaseTest {

    @Mock PanelLoader loader;
    @Mock PanelRepository panels;
    @Mock PanelAssignmentRepository assignments;
    @Mock EvaluationRepository evaluations;
    @Mock AbacGate abacGate;
    @Mock AuditService audit;

    DeletePanelUseCase useCase;

    UUID tenantId;
    UUID userId;
    UUID projectId;
    UUID panelId;
    UUID positionId;
    UUID departmentId;
    UUID versionId;

    @BeforeEach
    void setUp() {
        useCase = new DeletePanelUseCase(
                loader, panels, assignments, evaluations, abacGate, audit);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        panelId = UUID.randomUUID();
        positionId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        setManagerContext();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void collectingPanelDeletesSeatsFirstThenRowAndAuditsAndFreesSlot() {
        stubLoad(EvaluationPanelStatus.COLLECTING);

        useCase.delete(panelId, "collecting panel clean-up before re-creation");

        // Roster seats removed first (deterministic; FK also cascades).
        verify(assignments).deleteAllByTenantIdAndPanelId(tenantId, panelId);
        // Parent removed via the tenant-scoped deleter (not single-arg deleteById).
        verify(panels).deleteByIdAndTenantId(panelId, tenantId);
        // Removing a COLLECTING panel frees the partial-unique active-panel slot,
        // proven here by the row being physically deleted (status<>ARCHIVED index).
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        AuditEvent ev = captor.getValue();
        assertThat(ev.action()).isEqualTo(AuditAction.EVALUATION_PANEL_DELETED);
        assertThat(ev.entityType()).isEqualTo("EvaluationPanel");
        assertThat(ev.entityId()).isEqualTo(panelId);
    }

    @Test
    void collectingPanelDetachesEvaluationsThenDeletesPanelPreservingTheirWork() {
        stubLoad(EvaluationPanelStatus.COLLECTING);
        // A scored, still-INCOMPLETE per-evaluator sheet attached to this panel
        // (the prod repro: 1 evaluation referencing the panel via the RESTRICT FK).
        UUID evaluationId = UUID.randomUUID();
        UUID evaluatorUserId = UUID.randomUUID();
        EvaluationJpaEntity attached = new EvaluationJpaEntity(
                evaluationId, tenantId, projectId, positionId, versionId,
                evaluatorUserId, EvaluationStatus.INCOMPLETE);
        attached.setPanelId(panelId);
        attached.setEvaluatorRole(EvaluatorRole.HR_DIRECTOR);
        when(evaluations.findAllByTenantIdAndPanelId(tenantId, panelId))
                .thenReturn(List.of(attached));

        useCase.delete(panelId, "collecting panel clean-up before re-creation");

        // The evaluation is DETACHED (panel_id + evaluator_role nulled) and SAVED —
        // its work survives; it becomes a standalone non-panel sheet, same owner.
        ArgumentCaptor<EvaluationJpaEntity> savedCaptor =
                ArgumentCaptor.forClass(EvaluationJpaEntity.class);
        verify(evaluations).save(savedCaptor.capture());
        EvaluationJpaEntity saved = savedCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(evaluationId);
        assertThat(saved.getPanelId()).isNull();
        assertThat(saved.getEvaluatorRole()).isNull();
        // Still owned by the same evaluator, still INCOMPLETE, never deleted.
        assertThat(saved.getEvaluatorUserId()).isEqualTo(evaluatorUserId);
        assertThat(saved.getStatus()).isEqualTo(EvaluationStatus.INCOMPLETE);
        // The evaluation row is never deleted — EvaluationRepository (TenantAware)
        // deliberately exposes no delete/deleteById; PRESERVE is the only path.

        // Roster seats still removed, panel still hard-deleted, action audited.
        verify(assignments).deleteAllByTenantIdAndPanelId(tenantId, panelId);
        verify(panels).deleteByIdAndTenantId(panelId, tenantId);
        verify(audit).record(any(AuditEvent.class));
    }

    @ParameterizedTest
    @EnumSource(value = EvaluationPanelStatus.class,
            names = {"AWAITING_EVALUATIONS", "AVERAGED", "SUBMITTED", "APPROVED", "LOCKED", "ARCHIVED"})
    void nonCollectingRejectedWithNotDeletableCodeAndNothingDeleted(EvaluationPanelStatus status) {
        stubLoad(status);

        assertThatThrownBy(() ->
                useCase.delete(panelId, "trying to delete a non-collecting panel"))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode())
                        .isEqualTo("PANEL_NOT_DELETABLE"));

        verify(assignments, never()).deleteAllByTenantIdAndPanelId(any(), any());
        verify(panels, never()).deleteByIdAndTenantId(any(), any());
        verify(audit, never()).record(any());
        // No evaluations touched/detached when the guard rejects the delete.
        verify(evaluations, never()).findAllByTenantIdAndPanelId(any(), any());
        verify(evaluations, never()).save(any());
    }

    @Test
    void shortReasonRejectedBeforeLoad() {
        assertThatThrownBy(() -> useCase.delete(panelId, "no"))
                .isInstanceOf(ValidationException.class);
        verify(loader, never()).requirePanel(any(), any());
        verify(panels, never()).deleteByIdAndTenantId(any(), any());
    }

    @Test
    void nullReasonRejected() {
        assertThatThrownBy(() -> useCase.delete(panelId, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void missingManagePermissionThrows403() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("CLIENT_HR_DIRECTOR"),
                Set.of("EVALUATION_READ"), Set.of(), false, "ru-RU"));

        assertThatThrownBy(() ->
                useCase.delete(panelId, "collecting panel clean-up before re-creation"))
                .isInstanceOf(PermissionDeniedException.class);
        verify(loader, never()).requirePanel(any(), any());
    }

    @Test
    void crossTenantIdSurfacesAs404FromLoader() {
        when(loader.requirePanel(eq(panelId), eq(tenantId)))
                .thenThrow(new TenantAccessDeniedException());

        assertThatThrownBy(() ->
                useCase.delete(panelId, "collecting panel clean-up before re-creation"))
                .isInstanceOf(TenantAccessDeniedException.class);
        verify(panels, never()).deleteByIdAndTenantId(any(), any());
    }

    // ---------- helpers ----------

    private void stubLoad(EvaluationPanelStatus status) {
        EvaluationPanelJpaEntity panel = new EvaluationPanelJpaEntity(
                panelId, tenantId, projectId, positionId, versionId, status, 3);
        PositionJpaEntity position = new PositionJpaEntity(
                positionId, tenantId, projectId, departmentId, "P-001",
                Map.of("ru-RU", "Position"), null, null, null, null, PositionStatus.ACTIVE);
        when(loader.requirePanel(eq(panelId), eq(tenantId))).thenReturn(panel);
        when(loader.requirePosition(panel, tenantId)).thenReturn(position);
    }

    private void setManagerContext() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("CLIENT_HR_DIRECTOR"),
                Set.of("EVALUATION_PANEL_MANAGE"), Set.of(), false, "ru-RU"));
    }
}
