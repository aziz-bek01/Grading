package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.approval.application.CreateApprovalRequestCommand;
import uz.hrlab.grading.approval.application.CreateApprovalRequestUseCase;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequest;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BE-9 — submit AVERAGED panel to the CEO. Pins REQ-CEO-5 (no CEO step until
 * AVERAGED — submitting earlier is rejected server-side), the min-evaluators
 * re-check, and the REUSE of the EXISTING approval workflow: a single-step
 * {@code EVALUATION_PANEL} request gated by {@code EVALUATION_PANEL_APPROVE}
 * (REQ-CEO-1/-2) — no parallel approval engine.
 */
@ExtendWith(MockitoExtension.class)
class SubmitPanelToCeoUseCaseTest {

    @Mock PanelLoader loader;
    @Mock PanelRepository panels;
    @Mock PanelAssignmentRepository assignments;
    @Mock CreateApprovalRequestUseCase createApprovalRequest;
    @Mock AbacGate abacGate;
    @Mock AuditService audit;

    SubmitPanelToCeoUseCase useCase;

    UUID tenantId;
    UUID projectId;
    UUID positionId;
    UUID versionId;
    UUID departmentId;
    UUID panelId;

    @BeforeEach
    void setUp() {
        useCase = new SubmitPanelToCeoUseCase(
                loader, panels, assignments, createApprovalRequest, abacGate, audit);
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
        assertThatThrownBy(() -> useCase.submit(panelId))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void submitBeforeAveragedIsRejectedServerSide() {
        setContext(Set.of("EVALUATION_PANEL_MANAGE"));
        stubPanelAndPosition(EvaluationPanelStatus.AWAITING_EVALUATIONS);

        assertThatThrownBy(() -> useCase.submit(panelId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("PANEL_NOT_AVERAGED");
        verify(createApprovalRequest, never()).createSystem(any());
    }

    @Test
    void belowFloorCompletedSheetsBlocksCeoSubmit() {
        setContext(Set.of("EVALUATION_PANEL_MANAGE"));
        stubPanelAndPosition(EvaluationPanelStatus.AVERAGED);
        // Only 2 completed seats but min is 3.
        when(assignments.findAllByTenantIdAndPanelId(tenantId, panelId)).thenReturn(List.of(
                completedSeat(), completedSeat()));

        assertThatThrownBy(() -> useCase.submit(panelId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ROSTER_BELOW_FLOOR");
        verify(createApprovalRequest, never()).createSystem(any());
    }

    @Test
    void averagedPanelOpensSingleStepCeoApprovalRequestAndTransitions() {
        setContext(Set.of("EVALUATION_PANEL_MANAGE"));
        EvaluationPanelJpaEntity panel = stubPanelAndPosition(EvaluationPanelStatus.AVERAGED);
        when(assignments.findAllByTenantIdAndPanelId(tenantId, panelId)).thenReturn(List.of(
                completedSeat(), completedSeat(), completedSeat()));
        UUID approvalId = UUID.randomUUID();
        when(createApprovalRequest.createSystem(any())).thenReturn(approvalRequest(approvalId));

        useCase.submit(panelId);

        // Panel transitioned + persisted.
        assertThat(panel.getStatus()).isEqualTo(EvaluationPanelStatus.SUBMITTED);
        assertThat(panel.getSubmittedAt()).isNotNull();
        verify(panels).save(panel);

        // EXACT reuse: EVALUATION_PANEL entity + EVALUATION_PANEL_APPROVE single step.
        ArgumentCaptor<CreateApprovalRequestCommand> cmd =
                ArgumentCaptor.forClass(CreateApprovalRequestCommand.class);
        verify(createApprovalRequest).createSystem(cmd.capture());
        assertThat(cmd.getValue().entityType()).isEqualTo(ApprovalEntityType.EVALUATION_PANEL);
        assertThat(cmd.getValue().entityId()).isEqualTo(panelId);
        assertThat(cmd.getValue().steps()).hasSize(1);
        assertThat(cmd.getValue().steps().get(0).requiredPermission())
                .isEqualTo("EVALUATION_PANEL_APPROVE");

        ArgumentCaptor<AuditEvent> audited = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(audited.capture());
        assertThat(audited.getValue().action())
                .isEqualTo(AuditAction.EVALUATION_PANEL_SUBMITTED_TO_CEO);
        assertThat(audited.getValue().reason()).contains(approvalId.toString());
    }

    // --------------------------------------------------------------- helpers

    private EvaluationPanelJpaEntity stubPanelAndPosition(EvaluationPanelStatus status) {
        EvaluationPanelJpaEntity panel = new EvaluationPanelJpaEntity(
                panelId, tenantId, projectId, positionId, versionId, status, 3);
        PositionJpaEntity position = new PositionJpaEntity(
                positionId, tenantId, projectId, departmentId, "P-001",
                Map.of("ru-RU", "Кассир"), null, null, null, null, PositionStatus.ACTIVE);
        when(loader.requirePanel(panelId, tenantId)).thenReturn(panel);
        when(loader.requirePosition(panel, tenantId)).thenReturn(position);
        return panel;
    }

    private PanelAssignmentJpaEntity completedSeat() {
        return new PanelAssignmentJpaEntity(
                UUID.randomUUID(), tenantId, panelId, UUID.randomUUID(),
                EvaluatorRole.ADDITIONAL, PanelAssignmentStatus.COMPLETED);
    }

    private ApprovalRequest approvalRequest(UUID id) {
        return new ApprovalRequest(id, tenantId, projectId, ApprovalEntityType.EVALUATION_PANEL,
                panelId, UUID.randomUUID(), OffsetDateTime.now(),
                ApprovalRequestStatus.PENDING, null, List.of());
    }

    private void setContext(Set<String> permissions) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(projectId), Set.of(),
                permissions, Set.of(), false, "ru-RU"));
    }
}
