package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.Evaluation;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;
import uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BE-6 — roster lock {@code COLLECTING -> AWAITING_EVALUATIONS}. Pins the
 * server-side roster validation (REQ-AVG-5): below the floor or missing a
 * mandatory role is REJECTED server-side, never silently allowed; and on a valid
 * roster one DRAFT evaluation is created per active seat by REUSING
 * {@link CreateEvaluationUseCase} (no forked create path).
 */
@ExtendWith(MockitoExtension.class)
class LockRosterUseCaseTest {

    @Mock PanelLoader loader;
    @Mock PanelRepository panels;
    @Mock PanelAssignmentRepository assignments;
    @Mock CreateEvaluationUseCase createEvaluation;
    @Mock AbacGate abacGate;
    @Mock AuditService audit;

    LockRosterUseCase useCase;

    UUID tenantId;
    UUID projectId;
    UUID positionId;
    UUID versionId;
    UUID departmentId;
    UUID panelId;

    @BeforeEach
    void setUp() {
        useCase = new LockRosterUseCase(loader, panels, assignments, createEvaluation, abacGate, audit);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        positionId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        panelId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void withoutManagePermissionIsForbidden() {
        setContext(Set.of("EVALUATION_READ"));
        assertThatThrownBy(() -> useCase.lockRoster(panelId))
                .isInstanceOf(PermissionDeniedException.class);
        verify(loader, never()).requirePanel(any(), any());
    }

    @Test
    void belowFloorIsRejectedServerSide() {
        setContext(Set.of("EVALUATION_PANEL_MANAGE"));
        stubPanelAndPosition(EvaluationPanelStatus.COLLECTING, 3);
        // Only 2 active seats (covers 2 of 3 mandatory roles) — below the floor of 3.
        when(assignments.findAllByTenantIdAndPanelId(tenantId, panelId)).thenReturn(List.of(
                seat(EvaluatorRole.HR_DIRECTOR, PanelAssignmentStatus.ASSIGNED),
                seat(EvaluatorRole.DEPARTMENT_DIRECTOR, PanelAssignmentStatus.ASSIGNED)));

        assertThatThrownBy(() -> useCase.lockRoster(panelId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ROSTER_BELOW_FLOOR");
        verify(createEvaluation, never()).create(any());
        verify(panels, never()).save(any());
    }

    @Test
    void missingMandatoryRoleIsRejectedEvenWhenCountMeetsFloor() {
        setContext(Set.of("EVALUATION_PANEL_MANAGE"));
        stubPanelAndPosition(EvaluationPanelStatus.COLLECTING, 3);
        // 3 seats meet the count floor, but EXTERNAL_EXPERT is absent (2x ADDITIONAL).
        when(assignments.findAllByTenantIdAndPanelId(tenantId, panelId)).thenReturn(List.of(
                seat(EvaluatorRole.HR_DIRECTOR, PanelAssignmentStatus.ASSIGNED),
                seat(EvaluatorRole.DEPARTMENT_DIRECTOR, PanelAssignmentStatus.ASSIGNED),
                seat(EvaluatorRole.ADDITIONAL, PanelAssignmentStatus.ASSIGNED)));

        assertThatThrownBy(() -> useCase.lockRoster(panelId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("MANDATORY_ROLES_MISSING");
        verify(createEvaluation, never()).create(any());
    }

    @Test
    void withdrawnSeatsDoNotCountTowardTheFloor() {
        setContext(Set.of("EVALUATION_PANEL_MANAGE"));
        stubPanelAndPosition(EvaluationPanelStatus.COLLECTING, 3);
        // 3 mandatory roles present but one is WITHDRAWN -> only 2 active -> below floor.
        when(assignments.findAllByTenantIdAndPanelId(tenantId, panelId)).thenReturn(List.of(
                seat(EvaluatorRole.HR_DIRECTOR, PanelAssignmentStatus.ASSIGNED),
                seat(EvaluatorRole.DEPARTMENT_DIRECTOR, PanelAssignmentStatus.ASSIGNED),
                seat(EvaluatorRole.EXTERNAL_EXPERT, PanelAssignmentStatus.WITHDRAWN)));

        assertThatThrownBy(() -> useCase.lockRoster(panelId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ROSTER_BELOW_FLOOR");
    }

    @Test
    void notCollectingPanelCannotBeLocked() {
        setContext(Set.of("EVALUATION_PANEL_MANAGE"));
        stubPanelAndPosition(EvaluationPanelStatus.AWAITING_EVALUATIONS, 3);

        assertThatThrownBy(() -> useCase.lockRoster(panelId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("PANEL_NOT_COLLECTING");
        verify(assignments, never()).findAllByTenantIdAndPanelId(any(), any());
    }

    @Test
    void validRosterCreatesOneEvaluationPerSeatAndTransitions() {
        setContext(Set.of("EVALUATION_PANEL_MANAGE"));
        EvaluationPanelJpaEntity panel = stubPanelAndPosition(EvaluationPanelStatus.COLLECTING, 3);
        PanelAssignmentJpaEntity hr = seat(EvaluatorRole.HR_DIRECTOR, PanelAssignmentStatus.ASSIGNED);
        PanelAssignmentJpaEntity dept = seat(EvaluatorRole.DEPARTMENT_DIRECTOR, PanelAssignmentStatus.ASSIGNED);
        PanelAssignmentJpaEntity ext = seat(EvaluatorRole.EXTERNAL_EXPERT, PanelAssignmentStatus.ASSIGNED);
        when(assignments.findAllByTenantIdAndPanelId(tenantId, panelId))
                .thenReturn(List.of(hr, dept, ext));
        when(createEvaluation.create(any())).thenAnswer(inv -> draftEvaluation());

        useCase.lockRoster(panelId);

        // One reuse of CreateEvaluationUseCase per active seat (no fork).
        verify(createEvaluation, times(3)).create(any());
        // Each seat's evaluation_id is linked back.
        verify(assignments, times(3)).save(any());
        assertThat(hr.getEvaluationId()).isNotNull();
        assertThat(dept.getEvaluationId()).isNotNull();
        assertThat(ext.getEvaluationId()).isNotNull();
        // Panel transitioned + persisted.
        assertThat(panel.getStatus()).isEqualTo(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        verify(panels).save(panel);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.EVALUATION_PANEL_ROSTER_LOCKED);
        assertThat(captor.getValue().reason()).contains("mandatoryRolesSatisfied=true");
    }

    @Test
    void extraAdditionalEvaluatorsAreIncludedWhenMandatoryTrioPresent() {
        setContext(Set.of("EVALUATION_PANEL_MANAGE"));
        stubPanelAndPosition(EvaluationPanelStatus.COLLECTING, 3);
        when(assignments.findAllByTenantIdAndPanelId(tenantId, panelId)).thenReturn(List.of(
                seat(EvaluatorRole.HR_DIRECTOR, PanelAssignmentStatus.ASSIGNED),
                seat(EvaluatorRole.DEPARTMENT_DIRECTOR, PanelAssignmentStatus.ASSIGNED),
                seat(EvaluatorRole.EXTERNAL_EXPERT, PanelAssignmentStatus.ASSIGNED),
                seat(EvaluatorRole.ADDITIONAL, PanelAssignmentStatus.ASSIGNED)));
        when(createEvaluation.create(any())).thenAnswer(inv -> draftEvaluation());

        useCase.lockRoster(panelId);

        verify(createEvaluation, times(4)).create(any()); // trio + 1 extra
    }

    // --------------------------------------------------------------- helpers

    private EvaluationPanelJpaEntity stubPanelAndPosition(EvaluationPanelStatus status, int min) {
        EvaluationPanelJpaEntity panel = new EvaluationPanelJpaEntity(
                panelId, tenantId, projectId, positionId, versionId, status, min);
        PositionJpaEntity position = new PositionJpaEntity(
                positionId, tenantId, projectId, departmentId, "P-001",
                Map.of("ru-RU", "Кассир"), null, null, null, null, PositionStatus.ACTIVE);
        when(loader.requirePanel(panelId, tenantId)).thenReturn(panel);
        when(loader.requirePosition(panel, tenantId)).thenReturn(position);
        return panel;
    }

    private final List<UUID> usedEvaluators = new ArrayList<>();

    private PanelAssignmentJpaEntity seat(EvaluatorRole role, PanelAssignmentStatus status) {
        UUID evaluator = UUID.randomUUID();
        usedEvaluators.add(evaluator);
        return new PanelAssignmentJpaEntity(
                UUID.randomUUID(), tenantId, panelId, evaluator, role, status);
    }

    private Evaluation draftEvaluation() {
        return new Evaluation(UUID.randomUUID(), tenantId, projectId, positionId, versionId,
                UUID.randomUUID(), null, null, EvaluationStatus.DRAFT,
                BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null, null, null, null, null, null, null);
    }

    private void setContext(Set<String> permissions) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(projectId), Set.of(),
                permissions, Set.of(), false, "ru-RU"));
    }
}
