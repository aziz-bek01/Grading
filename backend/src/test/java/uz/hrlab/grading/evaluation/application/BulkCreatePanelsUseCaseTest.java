package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.api.BulkCreatePanelsResponse;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
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
 * BE-1/BE-2/BE-6/BE-7/BE-8 — {@link BulkCreatePanelsUseCase} per-row failure
 * collector. Mocks the inner {@link CreatePanelUseCase} + {@link AssignEvaluatorUseCase}
 * so the focus is the orchestration contract: valid rows count toward
 * {@code created}; ABAC / cross-tenant denial → {@code ACCESS_DENIED} keyed on
 * position_id (siblings still created, none created twice); duplicate-active →
 * {@code ALREADY_EXISTS}; a per-seat assign failure → {@code ROSTER_PARTIAL}
 * (panel still created); 3-4 externals assign without per-role uniqueness; ONE
 * wrapper {@code EVALUATION_PANEL_BULK_CREATED} audit always emitted. The per-row
 * security checks themselves are owned (and tested) by CreatePanelUseCase /
 * AssignEvaluatorUseCase — NOT duplicated here.
 */
@ExtendWith(MockitoExtension.class)
class BulkCreatePanelsUseCaseTest {

    @Mock CreatePanelUseCase createPanelUseCase;
    @Mock AssignEvaluatorUseCase assignEvaluatorUseCase;
    @Mock AuditService audit;

    BulkCreatePanelsUseCase useCase;

    UUID tenantId;
    UUID userId;
    UUID projectId;
    UUID versionId;

    @BeforeEach
    void setUp() {
        useCase = new BulkCreatePanelsUseCase(createPanelUseCase, assignEvaluatorUseCase, audit);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        setManagerContext();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // --------------------------------------------------------------- happy path

    @Test
    void allPositionsCreatedAndSharedRosterAssignedToEach() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        when(createPanelUseCase.create(any()))
                .thenAnswer(inv -> panel(UUID.randomUUID()));

        List<BulkCreatePanelsCommand.RosterSeat> roster = List.of(
                seat(EvaluatorRole.HR_DIRECTOR),
                seat(EvaluatorRole.DEPARTMENT_DIRECTOR),
                seat(EvaluatorRole.EXTERNAL_EXPERT));

        BulkCreatePanelsResponse res = useCase.execute(
                new BulkCreatePanelsCommand(versionId, List.of(p1, p2), roster));

        assertThat(res.created()).isEqualTo(2);
        assertThat(res.failed()).isEmpty();
        // Each position routed through the single-panel create — no shortcut path.
        verify(createPanelUseCase, times(2)).create(any());
        // Shared roster (3 seats) assigned to each of the 2 panels => 6 assigns.
        verify(assignEvaluatorUseCase, times(6)).assign(any(), any(), any());

        // BE-6 — one wrapper audit event with the commission counts.
        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(ev.capture());
        assertThat(ev.getValue().action()).isEqualTo(AuditAction.EVALUATION_PANEL_BULK_CREATED);
        assertThat(ev.getValue().reason())
                .contains("requested_count=2")
                .contains("created_count=2")
                .contains("failed_count=0");
    }

    @Test
    void fourExternalsAssignedNoPerRoleUniqueness() {
        // BE-7 — one EXTERNAL_EXPERT mandatory seat + extra externals as ADDITIONAL;
        // panel_assignments has no per-role uniqueness, so all four assign.
        UUID p1 = UUID.randomUUID();
        when(createPanelUseCase.create(any())).thenReturn(panel(UUID.randomUUID()));
        List<BulkCreatePanelsCommand.RosterSeat> roster = List.of(
                seat(EvaluatorRole.HR_DIRECTOR),
                seat(EvaluatorRole.DEPARTMENT_DIRECTOR),
                seat(EvaluatorRole.EXTERNAL_EXPERT),
                seat(EvaluatorRole.ADDITIONAL),
                seat(EvaluatorRole.ADDITIONAL),
                seat(EvaluatorRole.ADDITIONAL));

        BulkCreatePanelsResponse res = useCase.execute(
                new BulkCreatePanelsCommand(versionId, List.of(p1), roster));

        assertThat(res.created()).isEqualTo(1);
        assertThat(res.failed()).isEmpty();
        // 1 mandatory expert + 3 additional externals + HR + dept = 6 seats, all assigned.
        verify(assignEvaluatorUseCase, times(6)).assign(any(), any(), any());
    }

    @Test
    void minThreeNotEnforcedAtCreate_emptyRosterStillCreatesCollectingPanel() {
        // BE-7 — bulk create does NOT enforce min-3; it only assigns the seats the
        // admin provided. An empty roster still opens the COLLECTING panel (the
        // lock step is the enforcement point, not create).
        UUID p1 = UUID.randomUUID();
        when(createPanelUseCase.create(any())).thenReturn(panel(UUID.randomUUID()));

        BulkCreatePanelsResponse res = useCase.execute(
                new BulkCreatePanelsCommand(versionId, List.of(p1), List.of()));

        assertThat(res.created()).isEqualTo(1);
        assertThat(res.failed()).isEmpty();
        verify(assignEvaluatorUseCase, never()).assign(any(), any(), any());
    }

    // ----------------------------------------------------------- per-row failures

    @Test
    void alreadyPaneledRowSkippedWithAlreadyExists_siblingsCreatedNoneTwice() {
        UUID ok = UUID.randomUUID();
        UUID dup = UUID.randomUUID();
        when(createPanelUseCase.create(any())).thenAnswer(inv -> {
            CreatePanelCommand cmd = inv.getArgument(0);
            if (cmd.positionId().equals(dup)) {
                // CreatePanelUseCase's relocated one-active-panel guard.
                throw new ValidationException(
                        "An active panel already exists for this position and methodology version");
            }
            return panel(UUID.randomUUID());
        });

        BulkCreatePanelsResponse res = useCase.execute(
                new BulkCreatePanelsCommand(versionId, List.of(ok, dup), List.of()));

        assertThat(res.created()).isEqualTo(1);
        assertThat(res.failed()).hasSize(1);
        BulkCreatePanelsResponse.Failure f = res.failed().get(0);
        assertThat(f.positionId()).isEqualTo(dup);
        assertThat(f.errorCode()).isEqualTo("ALREADY_EXISTS");
        assertThat(f.seatFailures()).isNull();
        // The already-paneled row is never created — exactly one create succeeds.
        verify(createPanelUseCase, times(2)).create(any());
    }

    @Test
    void crossTenantOrAbacRowSkippedWithAccessDenied_siblingsCreated() {
        // BE-8 — a position from another tenant OR outside the caller's subtree
        // → ACCESS_DENIED for that row; in-scope siblings still created.
        UUID inScope = UUID.randomUUID();
        UUID denied = UUID.randomUUID();
        when(createPanelUseCase.create(any())).thenAnswer(inv -> {
            CreatePanelCommand cmd = inv.getArgument(0);
            if (cmd.positionId().equals(denied)) {
                throw new TenantAccessDeniedException();
            }
            return panel(UUID.randomUUID());
        });

        BulkCreatePanelsResponse res = useCase.execute(
                new BulkCreatePanelsCommand(versionId, List.of(inScope, denied), List.of()));

        assertThat(res.created()).isEqualTo(1);
        assertThat(res.failed()).hasSize(1);
        assertThat(res.failed().get(0).positionId()).isEqualTo(denied);
        assertThat(res.failed().get(0).errorCode()).isEqualTo("ACCESS_DENIED");
        assertThat(res.failed().get(0).seatFailures()).isNull();
    }

    @Test
    void allRowsAccessDeniedCreatesNoPanels() {
        // BE-8 — dept-director caller bulk-creating for a department OUTSIDE their
        // scope: every row ACCESS_DENIED, no panel created.
        when(createPanelUseCase.create(any())).thenThrow(new TenantAccessDeniedException());

        BulkCreatePanelsResponse res = useCase.execute(
                new BulkCreatePanelsCommand(versionId,
                        List.of(UUID.randomUUID(), UUID.randomUUID()), List.of()));

        assertThat(res.created()).isZero();
        assertThat(res.failed()).hasSize(2);
        assertThat(res.failed()).allSatisfy(
                f -> assertThat(f.errorCode()).isEqualTo("ACCESS_DENIED"));
        verify(assignEvaluatorUseCase, never()).assign(any(), any(), any());
    }

    // ------------------------------------------------------------ roster partial

    @Test
    void seatFailureFlagsRowRosterPartialButPanelStillCreated() {
        // BE-2 — a partial roster failure (one evaluator not assignable) is
        // reported at the position-row level as ROSTER_PARTIAL with a nested
        // per-seat reason list; the panel is still created (counted).
        UUID p1 = UUID.randomUUID();
        when(createPanelUseCase.create(any())).thenReturn(panel(UUID.randomUUID()));

        UUID badUser = UUID.randomUUID();
        when(assignEvaluatorUseCase.assign(any(), any(), any())).thenAnswer(inv -> {
            UUID seatUser = inv.getArgument(1);
            if (seatUser.equals(badUser)) {
                throw new ValidationException(
                        "EVALUATOR_ALREADY_ASSIGNED: this evaluator is already on the panel");
            }
            return null;
        });

        List<BulkCreatePanelsCommand.RosterSeat> roster = List.of(
                seat(EvaluatorRole.HR_DIRECTOR),
                new BulkCreatePanelsCommand.RosterSeat(badUser, EvaluatorRole.EXTERNAL_EXPERT));

        BulkCreatePanelsResponse res = useCase.execute(
                new BulkCreatePanelsCommand(versionId, List.of(p1), roster));

        assertThat(res.created()).isEqualTo(1); // panel created despite the bad seat
        assertThat(res.failed()).hasSize(1);
        BulkCreatePanelsResponse.Failure f = res.failed().get(0);
        assertThat(f.positionId()).isEqualTo(p1);
        assertThat(f.errorCode()).isEqualTo("ROSTER_PARTIAL");
        assertThat(f.seatFailures()).hasSize(1);
        assertThat(f.seatFailures().get(0).evaluatorUserId()).isEqualTo(badUser);
        assertThat(f.seatFailures().get(0).evaluatorRole()).isEqualTo("EXTERNAL_EXPERT");
        assertThat(f.seatFailures().get(0).reason()).contains("VALIDATION");
    }

    @Test
    void seatAccessDeniedReportedWithoutExistenceReveal() {
        UUID p1 = UUID.randomUUID();
        when(createPanelUseCase.create(any())).thenReturn(panel(UUID.randomUUID()));
        when(assignEvaluatorUseCase.assign(any(), any(), any()))
                .thenThrow(new TenantAccessDeniedException());

        BulkCreatePanelsResponse res = useCase.execute(new BulkCreatePanelsCommand(
                versionId, List.of(p1), List.of(seat(EvaluatorRole.EXTERNAL_EXPERT))));

        assertThat(res.created()).isEqualTo(1);
        assertThat(res.failed()).hasSize(1);
        assertThat(res.failed().get(0).errorCode()).isEqualTo("ROSTER_PARTIAL");
        assertThat(res.failed().get(0).seatFailures().get(0).reason()).contains("ACCESS_DENIED");
    }

    // ------------------------------------------------------------ guard / validation

    @Test
    void missingManagePermissionThrows403() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId), Set.of("CLIENT_HR_DIRECTOR"),
                Set.of("EVALUATION_READ"), Set.of(), false, "ru-RU"));

        assertThatThrownBy(() -> useCase.execute(new BulkCreatePanelsCommand(
                versionId, List.of(UUID.randomUUID()), List.of())))
                .isInstanceOf(PermissionDeniedException.class);
        verify(createPanelUseCase, never()).create(any());
    }

    @Test
    void emptyPositionIdsRejected() {
        assertThatThrownBy(() -> useCase.execute(new BulkCreatePanelsCommand(
                versionId, List.of(), List.of())))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void missingMethodologyVersionRejected() {
        assertThatThrownBy(() -> useCase.execute(new BulkCreatePanelsCommand(
                null, List.of(UUID.randomUUID()), List.of())))
                .isInstanceOf(ValidationException.class);
    }

    // --------------------------------------------------------------- helpers

    private EvaluationPanel panel(UUID id) {
        return new EvaluationPanel(id, tenantId, projectId, UUID.randomUUID(), versionId,
                EvaluationPanelStatus.COLLECTING, 3, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                OffsetDateTime.now());
    }

    private BulkCreatePanelsCommand.RosterSeat seat(EvaluatorRole role) {
        return new BulkCreatePanelsCommand.RosterSeat(UUID.randomUUID(), role);
    }

    private void setManagerContext() {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId), Set.of("CLIENT_HR_DIRECTOR"),
                Set.of("EVALUATION_PANEL_MANAGE"), Set.of(), false, "ru-RU"));
    }
}
