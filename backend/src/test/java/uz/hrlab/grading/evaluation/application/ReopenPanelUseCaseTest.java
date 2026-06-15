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
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Defect-2 BE — {@link ReopenPanelUseCase}. The manual controller entrypoint
 * {@code reopen(panelId)} and the approval-coupling entrypoint
 * {@code onChangesRequested(...)} share ONE reopen body ({@code applyReopen}).
 * These tests prove the controller path reaches the SAME body (same mutations,
 * same EVALUATION_PANEL_REOPENED audit, same SUBMITTED/AVERAGED guard) — there is
 * no second reopen implementation.
 */
@ExtendWith(MockitoExtension.class)
class ReopenPanelUseCaseTest {

    @Mock PanelLoader loader;
    @Mock PanelRepository panels;
    @Mock PanelFactorAverageRepository averages;
    @Mock AbacGate abacGate;
    @Mock AuditService audit;

    ReopenPanelUseCase useCase;

    UUID tenantId;
    UUID userId;
    UUID projectId;
    UUID panelId;
    UUID positionId;
    UUID departmentId;
    UUID versionId;

    @BeforeEach
    void setUp() {
        useCase = new ReopenPanelUseCase(panels, averages, audit, loader, abacGate);
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

    @ParameterizedTest
    @EnumSource(value = EvaluationPanelStatus.class, names = {"SUBMITTED", "AVERAGED"})
    void controllerReopenResetsPanelClearsAveragesAndAudits(EvaluationPanelStatus status) {
        EvaluationPanelJpaEntity panel = panelWithAverage(status);
        stubControllerLoad(panel);

        EvaluationPanel result = useCase.reopen(panelId);

        // SAME body as onChangesRequested: AWAITING_EVALUATIONS + cleared totals.
        assertThat(panel.getStatus()).isEqualTo(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        assertThat(panel.getRawTotalScore()).isNull();
        assertThat(panel.getDisplayedTotalScore()).isNull();
        assertThat(panel.getAveragedAt()).isNull();
        assertThat(panel.getAveragedBy()).isNull();
        assertThat(result.status()).isEqualTo(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        verify(panels).save(panel);
        verify(averages).deleteAllByTenantIdAndPanelId(tenantId, panelId);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.EVALUATION_PANEL_REOPENED);
        assertThat(captor.getValue().entityId()).isEqualTo(panelId);
    }

    @ParameterizedTest
    @EnumSource(value = EvaluationPanelStatus.class,
            names = {"COLLECTING", "AWAITING_EVALUATIONS", "APPROVED", "LOCKED", "ARCHIVED"})
    void controllerReopenIsNoopForNonReopenableStatus(EvaluationPanelStatus status) {
        EvaluationPanelJpaEntity panel = new EvaluationPanelJpaEntity(
                panelId, tenantId, projectId, positionId, versionId, status, 3);
        stubControllerLoad(panel);

        EvaluationPanel result = useCase.reopen(panelId);

        assertThat(result.status()).isEqualTo(status); // unchanged
        verify(panels, never()).save(any());
        verify(averages, never()).deleteAllByTenantIdAndPanelId(any(), any());
        verify(audit, never()).record(any());
    }

    @Test
    void controllerAndCouplingShareOneBody() {
        // Both entrypoints reopen a SUBMITTED panel identically — one body, no dup.
        EvaluationPanelJpaEntity viaController = panelWithAverage(EvaluationPanelStatus.SUBMITTED);
        stubControllerLoad(viaController);
        useCase.reopen(panelId);
        assertThat(viaController.getStatus()).isEqualTo(EvaluationPanelStatus.AWAITING_EVALUATIONS);

        EvaluationPanelJpaEntity viaCoupling = panelWithAverage(EvaluationPanelStatus.SUBMITTED);
        when(panels.findByIdAndTenantId(eq(panelId), eq(tenantId)))
                .thenReturn(Optional.of(viaCoupling));
        useCase.onChangesRequested(tenantId, panelId, userId);
        assertThat(viaCoupling.getStatus()).isEqualTo(EvaluationPanelStatus.AWAITING_EVALUATIONS);
    }

    @Test
    void controllerReopenMissingManagePermissionThrows403() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("CLIENT_HR_DIRECTOR"),
                Set.of("EVALUATION_READ"), Set.of(), false, "ru-RU"));

        assertThatThrownBy(() -> useCase.reopen(panelId))
                .isInstanceOf(PermissionDeniedException.class);
        verify(panels, never()).save(any());
    }

    @Test
    void controllerReopenUnknownIdIs404() {
        when(loader.requirePanel(eq(panelId), eq(tenantId)))
                .thenThrow(new TenantAccessDeniedException());

        assertThatThrownBy(() -> useCase.reopen(panelId))
                .isInstanceOf(TenantAccessDeniedException.class);
        verify(panels, never()).save(any());
    }

    @Test
    void controllerReopenDeniedByDepartmentScopeThrows404() {
        // A department-scoped manager whose write-scope excludes the panel's
        // department: the ABAC gate denies → 404 (TenantAccessDeniedException),
        // nothing reopened/cleared/audited. Guards the formerly-missing gate.
        EvaluationPanelJpaEntity panel = panelWithAverage(EvaluationPanelStatus.SUBMITTED);
        stubControllerLoad(panel);
        doThrow(new TenantAccessDeniedException())
                .when(abacGate)
                .enforceCanWriteInDepartment(any(), eq(projectId), eq(departmentId));

        assertThatThrownBy(() -> useCase.reopen(panelId))
                .isInstanceOf(TenantAccessDeniedException.class);
        verify(panels, never()).save(any());
        verify(averages, never()).deleteAllByTenantIdAndPanelId(any(), any());
        verify(audit, never()).record(any());
    }

    // ---------- helpers ----------

    /** Stubs the tenant-scoped panel + position load for the controller path. */
    private void stubControllerLoad(EvaluationPanelJpaEntity panel) {
        PositionJpaEntity position = new PositionJpaEntity(
                positionId, tenantId, projectId, departmentId, "P-001",
                Map.of("ru-RU", "Position"), null, null, null, null, PositionStatus.ACTIVE);
        when(loader.requirePanel(eq(panelId), eq(tenantId))).thenReturn(panel);
        when(loader.requirePosition(panel, tenantId)).thenReturn(position);
    }

    private EvaluationPanelJpaEntity panelWithAverage(EvaluationPanelStatus status) {
        EvaluationPanelJpaEntity panel = new EvaluationPanelJpaEntity(
                panelId, tenantId, projectId, positionId, versionId, status, 3);
        panel.setRawTotalScore(new BigDecimal("20.0000"));
        panel.setDisplayedTotalScore(new BigDecimal("20.00"));
        panel.setAveragedAt(OffsetDateTime.now());
        panel.setAveragedBy(UUID.randomUUID());
        return panel;
    }

    private void setManagerContext() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("CLIENT_HR_DIRECTOR"),
                Set.of("EVALUATION_PANEL_MANAGE"), Set.of(), false, "ru-RU"));
    }
}
