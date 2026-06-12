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
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

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
 * BE-4 — open a panel. Pins permission gate, tenant-scoped position + version
 * load, the APPROVED/LOCKED-version rule (reuse of the evaluation-create rule),
 * the ABAC department write gate, the relocated one-active-panel guard, and the
 * min_evaluators default of 3.
 */
@ExtendWith(MockitoExtension.class)
class CreatePanelUseCaseTest {

    @Mock PanelRepository panels;
    @Mock PositionRepository positions;
    @Mock MethodologyVersionRepository versions;
    @Mock AbacGate abacGate;
    @Mock AuditService audit;

    CreatePanelUseCase useCase;

    UUID tenantId;
    UUID projectId;
    UUID positionId;
    UUID versionId;
    UUID departmentId;

    @BeforeEach
    void setUp() {
        useCase = new CreatePanelUseCase(panels, positions, versions, abacGate, audit);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        positionId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void withoutManagePermissionIsForbidden() {
        setContext(Set.of("EVALUATION_READ"));
        assertThatThrownBy(() -> useCase.create(cmd(null)))
                .isInstanceOf(PermissionDeniedException.class);
        verify(positions, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void crossTenantPositionIsDeniedWithNoReveal() {
        setContext(Set.of("EVALUATION_PANEL_MANAGE"));
        when(positions.findByIdAndTenantId(positionId, tenantId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.create(cmd(null)))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void draftMethodologyVersionIsRejected() {
        setContext(Set.of("EVALUATION_PANEL_MANAGE"));
        stubPosition();
        stubVersion(MethodologyVersionStatus.DRAFT);
        assertThatThrownBy(() -> useCase.create(cmd(null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("APPROVED or LOCKED");
        verify(panels, never()).save(any());
    }

    @Test
    void abacDepartmentGateIsEnforced() {
        setContext(Set.of("EVALUATION_PANEL_MANAGE"));
        stubPosition();
        stubVersion(MethodologyVersionStatus.APPROVED);
        doThrow(new TenantAccessDeniedException())
                .when(abacGate).enforceCanWriteInDepartment(any(), eq(projectId), eq(departmentId));

        assertThatThrownBy(() -> useCase.create(cmd(null)))
                .isInstanceOf(TenantAccessDeniedException.class);
        verify(panels, never()).save(any());
    }

    @Test
    void oneActivePanelGuardRejectsDuplicate() {
        setContext(Set.of("EVALUATION_PANEL_MANAGE"));
        stubPosition();
        stubVersion(MethodologyVersionStatus.APPROVED);
        when(panels.existsByTenantIdAndPositionIdAndMethodologyVersionIdAndStatusNot(
                tenantId, positionId, versionId, EvaluationPanelStatus.ARCHIVED))
                .thenReturn(true);

        assertThatThrownBy(() -> useCase.create(cmd(null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("active panel already exists");
        verify(panels, never()).save(any());
    }

    @Test
    void createsCollectingPanelWithDefaultMinThreeAndAudits() {
        setContext(Set.of("EVALUATION_PANEL_MANAGE"));
        stubPosition();
        stubVersion(MethodologyVersionStatus.APPROVED);

        EvaluationPanel result = useCase.create(cmd(null)); // null min -> default 3

        ArgumentCaptor<EvaluationPanelJpaEntity> saved =
                ArgumentCaptor.forClass(EvaluationPanelJpaEntity.class);
        verify(panels).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(EvaluationPanelStatus.COLLECTING);
        assertThat(saved.getValue().getMinEvaluators()).isEqualTo(3);
        assertThat(saved.getValue().getProjectId()).isEqualTo(projectId);
        assertThat(result.status()).isEqualTo(EvaluationPanelStatus.COLLECTING);

        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(ev.capture());
        assertThat(ev.getValue().action()).isEqualTo(AuditAction.EVALUATION_PANEL_CREATED);
    }

    @Test
    void honoursExplicitMinEvaluatorsAboveFloor() {
        setContext(Set.of("EVALUATION_PANEL_MANAGE"));
        stubPosition();
        stubVersion(MethodologyVersionStatus.LOCKED);

        useCase.create(cmd(5));

        ArgumentCaptor<EvaluationPanelJpaEntity> saved =
                ArgumentCaptor.forClass(EvaluationPanelJpaEntity.class);
        verify(panels).save(saved.capture());
        assertThat(saved.getValue().getMinEvaluators()).isEqualTo(5);
    }

    @Test
    void minEvaluatorsBelowOneIsRejected() {
        setContext(Set.of("EVALUATION_PANEL_MANAGE"));
        assertThatThrownBy(() -> useCase.create(cmd(0)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("minEvaluators must be >= 1");
    }

    // --------------------------------------------------------------- helpers

    private void stubPosition() {
        PositionJpaEntity position = new PositionJpaEntity(
                positionId, tenantId, projectId, departmentId, "P-001",
                Map.of("ru-RU", "Кассир"), null, null, null, null, PositionStatus.ACTIVE);
        when(positions.findByIdAndTenantId(positionId, tenantId)).thenReturn(Optional.of(position));
    }

    private void stubVersion(MethodologyVersionStatus status) {
        MethodologyVersionJpaEntity v = org.mockito.Mockito.mock(MethodologyVersionJpaEntity.class);
        // getId is consulted only on the happy path (one-active pre-check + entity
        // build) — lenient so DRAFT-reject / ABAC-deny paths that exit earlier do
        // not trip strict-stubbing.
        org.mockito.Mockito.lenient().when(v.getId()).thenReturn(versionId);
        when(v.getStatus()).thenReturn(status);
        when(versions.findByIdAndTenantId(versionId, tenantId)).thenReturn(Optional.of(v));
    }

    private CreatePanelCommand cmd(Integer min) {
        return new CreatePanelCommand(positionId, versionId, min);
    }

    private void setContext(Set<String> permissions) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(projectId), Set.of(),
                permissions, Set.of(), false, "ru-RU"));
    }
}
