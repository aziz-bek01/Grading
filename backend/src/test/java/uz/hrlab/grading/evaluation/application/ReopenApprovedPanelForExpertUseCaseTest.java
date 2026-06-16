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
import uz.hrlab.grading.evaluation.domain.Evaluation;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;
import uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Feature 2 — {@link ReopenApprovedPanelForExpertUseCase}. Mockito-only (no
 * Docker) verification of the application-layer contract:
 * <ul>
 *   <li>only APPROVED is reopenable; other statuses rejected with a stable code;</li>
 *   <li>missing/blank reason rejected; permission + ABAC enforced;</li>
 *   <li>the GUC is SET LOCAL (carve-out enabled) before un-locking;</li>
 *   <li>prior LOCKED sheets flip LOCKED-&gt;COMPLETE and NO evaluation_scores are
 *       touched (the use case is given no score repository — by construction it
 *       cannot delete a score);</li>
 *   <li>the additional expert gets an ADDITIONAL seat + a DRAFT evaluation
 *       (reusing CreateEvaluationUseCase) and the seat is linked;</li>
 *   <li>the panel ends AWAITING_EVALUATIONS with cleared totals but the assigned
 *       grade HELD; the audit row is written.</li>
 * </ul>
 * The DB-trigger / GUC enforcement itself is covered by the Testcontainers
 * {@code PanelReopenForExpertIntegrationTest} (CI).
 */
@ExtendWith(MockitoExtension.class)
class ReopenApprovedPanelForExpertUseCaseTest {

    @Mock PanelLoader loader;
    @Mock PanelRepository panels;
    @Mock EvaluationRepository evaluations;
    @Mock PanelAssignmentRepository assignments;
    @Mock PanelFactorAverageRepository averages;
    @Mock CreateEvaluationUseCase createEvaluation;
    @Mock PanelReopenGuc reopenGuc;
    @Mock AbacGate abacGate;
    @Mock AuditService audit;

    ReopenApprovedPanelForExpertUseCase useCase;

    UUID tenantId;
    UUID userId;
    UUID projectId;
    UUID panelId;
    UUID positionId;
    UUID departmentId;
    UUID versionId;
    UUID expertId;

    @BeforeEach
    void setUp() {
        useCase = new ReopenApprovedPanelForExpertUseCase(loader, panels, evaluations,
                assignments, averages, createEvaluation, reopenGuc, abacGate, audit);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        panelId = UUID.randomUUID();
        positionId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        expertId = UUID.randomUUID();
        setManagerContext();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void reopenApprovedPanelUnlocksPriorSheetsAddsExpertAndStaysAwaiting() {
        EvaluationPanelJpaEntity panel = approvedPanelWithGradeAndAverage();
        stubLoad(panel);
        when(assignments.findByTenantIdAndPanelIdAndEvaluatorUserId(tenantId, panelId, expertId))
                .thenReturn(Optional.empty());
        EvaluationJpaEntity lockedA = sheet(EvaluationStatus.LOCKED);
        EvaluationJpaEntity lockedB = sheet(EvaluationStatus.LOCKED);
        when(evaluations.findAllByTenantIdAndPanelId(tenantId, panelId))
                .thenReturn(List.of(lockedA, lockedB));
        UUID newEvalId = UUID.randomUUID();
        when(createEvaluation.create(any())).thenReturn(draftEvaluation(newEvalId));

        EvaluationPanel result = useCase.reopen(panelId, expertId, "Adding an external SME after round one");

        // GUC carve-out enabled BEFORE the un-lock (allows LOCKED->COMPLETE this tx).
        verify(reopenGuc).enableForCurrentTransaction();
        // Prior sheets un-frozen LOCKED -> COMPLETE; lock metadata cleared.
        assertThat(lockedA.getStatus()).isEqualTo(EvaluationStatus.COMPLETE);
        assertThat(lockedB.getStatus()).isEqualTo(EvaluationStatus.COMPLETE);
        assertThat(lockedA.getLockedAt()).isNull();
        verify(evaluations, times(2)).save(any());

        // The additional expert: one DRAFT evaluation via CreateEvaluationUseCase,
        // ADDITIONAL role, linked to its new seat.
        ArgumentCaptor<CreateEvaluationCommand> cmd = ArgumentCaptor.forClass(CreateEvaluationCommand.class);
        verify(createEvaluation).create(cmd.capture());
        assertThat(cmd.getValue().panelId()).isEqualTo(panelId);
        assertThat(cmd.getValue().evaluatorUserId()).isEqualTo(expertId);
        assertThat(cmd.getValue().evaluatorRole()).isEqualTo(EvaluatorRole.ADDITIONAL);
        ArgumentCaptor<PanelAssignmentJpaEntity> seat = ArgumentCaptor.forClass(PanelAssignmentJpaEntity.class);
        verify(assignments).save(seat.capture());
        assertThat(seat.getValue().getEvaluatorRole()).isEqualTo(EvaluatorRole.ADDITIONAL);
        assertThat(seat.getValue().getEvaluatorUserId()).isEqualTo(expertId);
        assertThat(seat.getValue().getEvaluationId()).isEqualTo(newEvalId);

        // Panel reopened: AWAITING_EVALUATIONS, totals cleared, grade HELD.
        assertThat(panel.getStatus()).isEqualTo(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        assertThat(panel.getRawTotalScore()).isNull();
        assertThat(panel.getDisplayedTotalScore()).isNull();
        assertThat(panel.getAveragedAt()).isNull();
        assertThat(panel.getAveragedBy()).isNull();
        assertThat(panel.getAssignedGradeNumber()).isEqualTo(12); // grade NOT cleared
        assertThat(panel.getGradeBandId()).isNotNull();
        assertThat(result.status()).isEqualTo(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        verify(panels).save(panel);
        verify(averages).deleteAllByTenantIdAndPanelId(tenantId, panelId);

        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(ev.capture());
        assertThat(ev.getValue().action())
                .isEqualTo(AuditAction.EVALUATION_PANEL_REOPENED_FOR_EXPERT);
        assertThat(ev.getValue().entityId()).isEqualTo(panelId);
        assertThat(ev.getValue().reason()).contains("addedEvaluator=" + expertId);
    }

    @ParameterizedTest
    @EnumSource(value = EvaluationPanelStatus.class,
            names = {"COLLECTING", "AWAITING_EVALUATIONS", "AVERAGED", "SUBMITTED", "LOCKED", "ARCHIVED"})
    void nonApprovedPanelIsRejected(EvaluationPanelStatus status) {
        EvaluationPanelJpaEntity panel = new EvaluationPanelJpaEntity(
                panelId, tenantId, projectId, positionId, versionId, status, 3);
        stubLoad(panel);

        assertThatThrownBy(() -> useCase.reopen(panelId, expertId, "A valid reopen reason"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("APPROVED");
        verify(reopenGuc, never()).enableForCurrentTransaction();
        verify(createEvaluation, never()).create(any());
        verify(panels, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void missingPermissionIsForbidden() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId), Set.of("CLIENT_HR_DIRECTOR"),
                Set.of("EVALUATION_READ"), Set.of(), false, "ru-RU"));

        assertThatThrownBy(() -> useCase.reopen(panelId, expertId, "A valid reopen reason"))
                .isInstanceOf(PermissionDeniedException.class);
        verify(loader, never()).requirePanel(any(), any());
        verify(reopenGuc, never()).enableForCurrentTransaction();
    }

    @Test
    void blankReasonIsRejected() {
        assertThatThrownBy(() -> useCase.reopen(panelId, expertId, "no"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reason");
        verify(reopenGuc, never()).enableForCurrentTransaction();
        verify(createEvaluation, never()).create(any());
    }

    @Test
    void nullEvaluatorIsRejected() {
        assertThatThrownBy(() -> useCase.reopen(panelId, null, "A valid reopen reason"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("additional_evaluator_user_id");
        verify(reopenGuc, never()).enableForCurrentTransaction();
    }

    @Test
    void outOfSubtreeDepartmentScopeIs404AndNothingMutated() {
        EvaluationPanelJpaEntity panel = approvedPanelWithGradeAndAverage();
        stubLoad(panel);
        doThrow(new TenantAccessDeniedException())
                .when(abacGate).enforceCanWriteInDepartment(any(), eq(projectId), eq(departmentId));

        assertThatThrownBy(() -> useCase.reopen(panelId, expertId, "A valid reopen reason"))
                .isInstanceOf(TenantAccessDeniedException.class);
        verify(reopenGuc, never()).enableForCurrentTransaction();
        verify(createEvaluation, never()).create(any());
        verify(panels, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void duplicateActiveEvaluatorIsRejected() {
        EvaluationPanelJpaEntity panel = approvedPanelWithGradeAndAverage();
        stubLoad(panel);
        PanelAssignmentJpaEntity existing = new PanelAssignmentJpaEntity(
                UUID.randomUUID(), tenantId, panelId, expertId,
                EvaluatorRole.EXTERNAL_EXPERT, PanelAssignmentStatus.COMPLETED);
        when(assignments.findByTenantIdAndPanelIdAndEvaluatorUserId(tenantId, panelId, expertId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.reopen(panelId, expertId, "A valid reopen reason"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("EVALUATOR_ALREADY_ASSIGNED");
        verify(reopenGuc, never()).enableForCurrentTransaction();
        verify(createEvaluation, never()).create(any());
    }

    // --------------------------------------------------------------- helpers

    private void stubLoad(EvaluationPanelJpaEntity panel) {
        PositionJpaEntity position = new PositionJpaEntity(
                positionId, tenantId, projectId, departmentId, "P-001",
                Map.of("ru-RU", "Кассир"), null, null, null, null, PositionStatus.ACTIVE);
        when(loader.requirePanel(eq(panelId), eq(tenantId))).thenReturn(panel);
        when(loader.requirePosition(panel, tenantId)).thenReturn(position);
    }

    private EvaluationPanelJpaEntity approvedPanelWithGradeAndAverage() {
        EvaluationPanelJpaEntity panel = new EvaluationPanelJpaEntity(
                panelId, tenantId, projectId, positionId, versionId,
                EvaluationPanelStatus.APPROVED, 3);
        panel.setRawTotalScore(new BigDecimal("20.0000"));
        panel.setDisplayedTotalScore(new BigDecimal("20.00"));
        panel.setAveragedAt(OffsetDateTime.now());
        panel.setAveragedBy(UUID.randomUUID());
        panel.setAssignedGradeNumber(12);
        panel.setGradeBandId(UUID.randomUUID());
        return panel;
    }

    private EvaluationJpaEntity sheet(EvaluationStatus status) {
        EvaluationJpaEntity e = new EvaluationJpaEntity(
                UUID.randomUUID(), tenantId, projectId, positionId, versionId,
                UUID.randomUUID(), status);
        e.setPanelId(panelId);
        e.setLockedAt(OffsetDateTime.now());
        e.setLockedBy(UUID.randomUUID());
        return e;
    }

    private Evaluation draftEvaluation(UUID id) {
        return new Evaluation(id, tenantId, projectId, positionId, versionId,
                expertId, null, null, EvaluationStatus.DRAFT,
                BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null, null, null, null, null, null, null);
    }

    private void setManagerContext() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId), Set.of("CLIENT_HR_DIRECTOR"),
                Set.of("EVALUATION_PANEL_MANAGE"), Set.of(), false, "ru-RU"));
    }
}
