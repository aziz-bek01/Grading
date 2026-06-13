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
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

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
 * Defect-2 BE — {@link ArchivePanelUseCase}. Mirrors the evaluation archive
 * pattern / {@code DeletePanelUseCaseTest}. Asserts:
 * <ul>
 *   <li>archive from each working status ({@code AWAITING_EVALUATIONS},
 *       {@code AVERAGED}, {@code SUBMITTED}) sets status=ARCHIVED + archivedAt +
 *       archivedBy, saves, and audits EVALUATION_PANEL_ARCHIVED (frees the
 *       active-panel slot);</li>
 *   <li>committed/terminal statuses ({@code APPROVED}, {@code LOCKED},
 *       {@code ARCHIVED}) and {@code COLLECTING} → 400 {@code PANEL_NOT_ARCHIVABLE}
 *       (nothing saved or audited);</li>
 *   <li>reason required (missing/short → 400 before load);</li>
 *   <li>missing EVALUATION_PANEL_MANAGE → 403; cross-tenant id → 404.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ArchivePanelUseCaseTest {

    @Mock PanelLoader loader;
    @Mock PanelRepository panels;
    @Mock AbacGate abacGate;
    @Mock AuditService audit;

    ArchivePanelUseCase useCase;

    UUID tenantId;
    UUID userId;
    UUID projectId;
    UUID panelId;
    UUID positionId;
    UUID departmentId;
    UUID versionId;

    @BeforeEach
    void setUp() {
        useCase = new ArchivePanelUseCase(loader, panels, abacGate, audit);
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
    @EnumSource(value = EvaluationPanelStatus.class,
            names = {"AWAITING_EVALUATIONS", "AVERAGED", "SUBMITTED"})
    void workingStatusArchivesSetsTimestampsAndAudits(EvaluationPanelStatus status) {
        EvaluationPanelJpaEntity panel = stubLoad(status);

        EvaluationPanel result = useCase.archive(panelId, "cancelling this commission round");

        assertThat(panel.getStatus()).isEqualTo(EvaluationPanelStatus.ARCHIVED);
        assertThat(panel.getArchivedAt()).isNotNull();
        assertThat(panel.getArchivedBy()).isEqualTo(userId);
        assertThat(result.status()).isEqualTo(EvaluationPanelStatus.ARCHIVED);
        verify(panels).save(panel);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        AuditEvent ev = captor.getValue();
        assertThat(ev.action()).isEqualTo(AuditAction.EVALUATION_PANEL_ARCHIVED);
        assertThat(ev.entityType()).isEqualTo("EvaluationPanel");
        assertThat(ev.entityId()).isEqualTo(panelId);
    }

    @ParameterizedTest
    @EnumSource(value = EvaluationPanelStatus.class,
            names = {"COLLECTING", "APPROVED", "LOCKED", "ARCHIVED"})
    void nonArchivableRejectedWithCodeAndNothingSaved(EvaluationPanelStatus status) {
        stubLoad(status);

        assertThatThrownBy(() -> useCase.archive(panelId, "trying to archive a non-archivable panel"))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode())
                        .isEqualTo("PANEL_NOT_ARCHIVABLE"));

        verify(panels, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void shortReasonRejectedBeforeLoad() {
        assertThatThrownBy(() -> useCase.archive(panelId, "no"))
                .isInstanceOf(ValidationException.class);
        verify(loader, never()).requirePanel(any(), any());
        verify(panels, never()).save(any());
    }

    @Test
    void nullReasonRejected() {
        assertThatThrownBy(() -> useCase.archive(panelId, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void missingManagePermissionThrows403() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("CLIENT_HR_DIRECTOR"),
                Set.of("EVALUATION_READ"), Set.of(), false, "ru-RU"));

        assertThatThrownBy(() -> useCase.archive(panelId, "cancelling this commission round"))
                .isInstanceOf(PermissionDeniedException.class);
        verify(loader, never()).requirePanel(any(), any());
    }

    @Test
    void crossTenantIdSurfacesAs404FromLoader() {
        when(loader.requirePanel(eq(panelId), eq(tenantId)))
                .thenThrow(new TenantAccessDeniedException());

        assertThatThrownBy(() -> useCase.archive(panelId, "cancelling this commission round"))
                .isInstanceOf(TenantAccessDeniedException.class);
        verify(panels, never()).save(any());
    }

    // ---------- helpers ----------

    private EvaluationPanelJpaEntity stubLoad(EvaluationPanelStatus status) {
        EvaluationPanelJpaEntity panel = new EvaluationPanelJpaEntity(
                panelId, tenantId, projectId, positionId, versionId, status, 3);
        PositionJpaEntity position = new PositionJpaEntity(
                positionId, tenantId, projectId, departmentId, "P-001",
                Map.of("ru-RU", "Position"), null, null, null, null, PositionStatus.ACTIVE);
        when(loader.requirePanel(eq(panelId), eq(tenantId))).thenReturn(panel);
        when(loader.requirePosition(panel, tenantId)).thenReturn(position);
        return panel;
    }

    private void setManagerContext() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("CLIENT_HR_DIRECTOR"),
                Set.of("EVALUATION_PANEL_MANAGE"), Set.of(), false, "ru-RU"));
    }
}
