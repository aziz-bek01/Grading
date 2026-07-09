package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.ActorNameResolver;
import uz.hrlab.grading.access.application.DepartmentScopeFilter;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.access.infrastructure.UserDepartmentScopeRepository;
import uz.hrlab.grading.evaluation.api.PanelResponse;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;
import uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.organization.infrastructure.DepartmentHierarchyResolver;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * P0-B display-integrity — the "ЭКСПЕРТЫ N из M" count.
 *
 * <p>There is NO min-3 bypass: {@code LockRosterUseCase} requires the mandatory
 * trio (3 distinct roles) so a panel can NEVER leave COLLECTING with fewer than 3
 * active seats, and seats cannot be withdrawn after lock. But the LIST/DETAIL
 * "experts" count was computed from the CURRENTLY-ACTIVE assignment seats for
 * every panel, so a SUBMITTED/averaged panel could mislead the CEO if the live
 * roster count ever diverged from the sheets that fed the average.
 *
 * <p>The fix: for a panel that is NO LONGER collecting, the count reflects the
 * CONTRIBUTING sheets (the materialized {@code panel_factor_averages.evaluator_
 * count} denominator). Collecting panels keep the live roster count.
 */
@Tag("security")
@ExtendWith(MockitoExtension.class)
class PanelQueriesCountIntegrityTest {

    @Mock PanelRepository panels;
    @Mock PanelAssignmentRepository assignments;
    @Mock PanelFactorAverageRepository averages;
    @Mock EvaluationRepository evaluations;
    @Mock EvaluationScoreRepository scores;
    @Mock PositionRepository positions;
    @Mock FactorRepository factors;
    @Mock ActorNameResolver actorNames;
    @Mock AbacGate abacGate;
    @Mock DepartmentRepository departments;
    @Mock UserDepartmentScopeRepository departmentScopes;

    PanelQueries queries;

    UUID tenantId;
    UUID projectId;
    UUID positionId;
    UUID panelId;
    Pageable pageable = PageRequest.of(0, 50);

    @BeforeEach
    void setUp() {
        // Real (stateless) filter: CLIENT_HR_DIRECTOR is a tenant-wide bypass, so it
        // returns empty (unfiltered) — these count tests keep the unfiltered path.
        queries = new PanelQueries(panels, assignments, averages, evaluations, scores,
                positions, factors, actorNames, abacGate, departments, departmentScopes,
                new DepartmentScopeFilter(), new DepartmentHierarchyResolver(departments));
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        positionId = UUID.randomUUID();
        panelId = UUID.randomUUID();
        setResultViewerContext();
    }

    @AfterEach
    void tearDown() { TenantContextHolder.clear(); }

    @Test
    void submittedPanelCountReflectsContributingSheetsNotLiveActiveSeats() {
        // A SUBMITTED panel that was averaged over THREE completed sheets but whose
        // live roster currently shows ONE active seat must NOT display "1 из 1" —
        // it must display the 3 that fed the average.
        EvaluationPanelJpaEntity panel = panel(EvaluationPanelStatus.SUBMITTED);
        when(panels.findAllByTenantIdAndProjectId(eq(tenantId), eq(projectId), any()))
                .thenReturn(new PageImpl<>(List.of(panel)));
        stubPositionTitle();
        // Live roster currently has only ONE active seat.
        when(assignments.findAllByTenantIdAndPanelIdIn(eq(tenantId), any()))
                .thenReturn(List.of(seat(PanelAssignmentStatus.COMPLETED)));
        // The materialized average says THREE sheets contributed.
        when(averages.findEvaluatorCountsByPanelIds(eq(tenantId), any()))
                .thenReturn(List.of(view(panelId, 3)));

        Page<PanelResponse> page = queries.list(projectId, null, null, pageable);

        PanelResponse row = page.getContent().get(0);
        assertThat(row.evaluatorCount()).isEqualTo(3);   // contributing denominator
        assertThat(row.completedCount()).isEqualTo(3);
    }

    @Test
    void collectingPanelKeepsLiveRosterCount() {
        // A COLLECTING panel is the roster-building view — it MUST keep the live
        // active-seat count (the average denominator does not apply yet).
        EvaluationPanelJpaEntity panel = panel(EvaluationPanelStatus.COLLECTING);
        when(panels.findAllByTenantIdAndProjectId(eq(tenantId), eq(projectId), any()))
                .thenReturn(new PageImpl<>(List.of(panel)));
        stubPositionTitle();
        when(assignments.findAllByTenantIdAndPanelIdIn(eq(tenantId), any()))
                .thenReturn(List.of(
                        seat(PanelAssignmentStatus.ASSIGNED),
                        seat(PanelAssignmentStatus.ASSIGNED)));
        // No materialized average while collecting.
        when(averages.findEvaluatorCountsByPanelIds(eq(tenantId), any()))
                .thenReturn(List.of());

        Page<PanelResponse> page = queries.list(projectId, null, null, pageable);

        PanelResponse row = page.getContent().get(0);
        assertThat(row.evaluatorCount()).isEqualTo(2);   // live roster
        assertThat(row.completedCount()).isEqualTo(0);
    }

    @Test
    void nonCollectingPanelWithoutMaterializedAverageFallsBackToRosterCount() {
        // Defensive: a non-collecting panel whose averages were cleared (e.g. mid
        // reopen) has no denominator → fall back to the live roster count rather
        // than showing zero.
        EvaluationPanelJpaEntity panel = panel(EvaluationPanelStatus.AVERAGED);
        when(panels.findAllByTenantIdAndProjectId(eq(tenantId), eq(projectId), any()))
                .thenReturn(new PageImpl<>(List.of(panel)));
        stubPositionTitle();
        when(assignments.findAllByTenantIdAndPanelIdIn(eq(tenantId), any()))
                .thenReturn(List.of(
                        seat(PanelAssignmentStatus.COMPLETED),
                        seat(PanelAssignmentStatus.COMPLETED),
                        seat(PanelAssignmentStatus.COMPLETED)));
        when(averages.findEvaluatorCountsByPanelIds(eq(tenantId), any()))
                .thenReturn(List.of()); // no average row

        Page<PanelResponse> page = queries.list(projectId, null, null, pageable);

        assertThat(page.getContent().get(0).evaluatorCount()).isEqualTo(3);
    }

    // ---- REQ-CEO: optional status filter for the org-wide list (statuses-only) ----

    @Test
    void statusFilterSourcesPageFromStatusInAndReusesMapping() {
        // CEO org pull: ?status=SUBMITTED with NO project/position scope must source
        // the page from findAllByTenantIdAndStatusIn and reuse the SAME batched
        // mapping (contributing-denominator correction included).
        EvaluationPanelJpaEntity panel = panel(EvaluationPanelStatus.SUBMITTED);
        when(panels.findAllByTenantIdAndStatusIn(
                eq(tenantId), eq(List.of(EvaluationPanelStatus.SUBMITTED)), any()))
                .thenReturn(new PageImpl<>(List.of(panel)));
        stubPositionTitle();
        when(assignments.findAllByTenantIdAndPanelIdIn(eq(tenantId), any()))
                .thenReturn(List.of(seat(PanelAssignmentStatus.COMPLETED)));
        when(averages.findEvaluatorCountsByPanelIds(eq(tenantId), any()))
                .thenReturn(List.of(view(panelId, 3)));

        Page<PanelResponse> page = queries.list(
                null, null, List.of(EvaluationPanelStatus.SUBMITTED), pageable);

        // Mapping was reused (contributing denominator wins for a non-collecting panel).
        PanelResponse row = page.getContent().get(0);
        assertThat(row.status()).isEqualTo(EvaluationPanelStatus.SUBMITTED);
        assertThat(row.evaluatorCount()).isEqualTo(3);
        // The status-filtered query was the source; the unfiltered tenant query was not.
        org.mockito.Mockito.verify(panels).findAllByTenantIdAndStatusIn(
                eq(tenantId), eq(List.of(EvaluationPanelStatus.SUBMITTED)), any());
        org.mockito.Mockito.verify(panels, org.mockito.Mockito.never())
                .findAllByTenantId(eq(tenantId), any());
    }

    @Test
    void statusFilterIgnoredWhenProjectScopedKeepingExistingPath() {
        // A status filter combined with a projectId is intentionally ignored — the
        // existing project-scoped path runs unchanged (no findAllByTenantIdAndStatusIn).
        EvaluationPanelJpaEntity panel = panel(EvaluationPanelStatus.AVERAGED);
        when(panels.findAllByTenantIdAndProjectId(eq(tenantId), eq(projectId), any()))
                .thenReturn(new PageImpl<>(List.of(panel)));
        stubPositionTitle();
        when(assignments.findAllByTenantIdAndPanelIdIn(eq(tenantId), any()))
                .thenReturn(List.of(seat(PanelAssignmentStatus.COMPLETED)));
        when(averages.findEvaluatorCountsByPanelIds(eq(tenantId), any()))
                .thenReturn(List.of(view(panelId, 3)));

        queries.list(projectId, null, List.of(EvaluationPanelStatus.AVERAGED), pageable);

        org.mockito.Mockito.verify(panels).findAllByTenantIdAndProjectId(
                eq(tenantId), eq(projectId), any());
        org.mockito.Mockito.verify(panels, org.mockito.Mockito.never())
                .findAllByTenantIdAndStatusIn(eq(tenantId), any(), any());
    }

    @Test
    void nullStatusUsesUnfilteredTenantPathUnchanged() {
        // Backward compatibility: statuses == null + no scope falls through to the
        // pre-existing findAllByTenantId path (behavior byte-identical to before).
        EvaluationPanelJpaEntity panel = panel(EvaluationPanelStatus.COLLECTING);
        when(panels.findAllByTenantId(eq(tenantId), any()))
                .thenReturn(new PageImpl<>(List.of(panel)));
        stubPositionTitle();
        when(assignments.findAllByTenantIdAndPanelIdIn(eq(tenantId), any()))
                .thenReturn(List.of(seat(PanelAssignmentStatus.ASSIGNED)));
        when(averages.findEvaluatorCountsByPanelIds(eq(tenantId), any()))
                .thenReturn(List.of());

        queries.list(null, null, null, pageable);

        org.mockito.Mockito.verify(panels).findAllByTenantId(eq(tenantId), any());
        org.mockito.Mockito.verify(panels, org.mockito.Mockito.never())
                .findAllByTenantIdAndStatusIn(eq(tenantId), any(), any());
    }

    // --------------------------------------------------------------- helpers

    private EvaluationPanelJpaEntity panel(EvaluationPanelStatus status) {
        return new EvaluationPanelJpaEntity(
                panelId, tenantId, projectId, positionId, UUID.randomUUID(), status, 3);
    }

    private PanelAssignmentJpaEntity seat(PanelAssignmentStatus status) {
        return new PanelAssignmentJpaEntity(
                UUID.randomUUID(), tenantId, panelId, UUID.randomUUID(),
                EvaluatorRole.ADDITIONAL, status);
    }

    private PanelFactorAverageRepository.PanelEvaluatorCountView view(UUID panel, int count) {
        return new PanelFactorAverageRepository.PanelEvaluatorCountView() {
            @Override public UUID getPanelId() { return panel; }
            @Override public int getEvaluatorCount() { return count; }
        };
    }

    private void stubPositionTitle() {
        PositionJpaEntity p = new PositionJpaEntity(
                positionId, tenantId, projectId, UUID.randomUUID(), "P-001",
                Map.of("ru-RU", "Кассир"), null, null, null, null, PositionStatus.ACTIVE);
        lenient().when(positions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(p));
    }

    private void setResultViewerContext() {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(projectId),
                Set.of(RoleCodes.CLIENT_HR_DIRECTOR),
                Set.of("EVALUATION_READ", "CAMPAIGN_RESULTS_VIEW"), Set.of(), false, "ru-RU"));
    }
}
