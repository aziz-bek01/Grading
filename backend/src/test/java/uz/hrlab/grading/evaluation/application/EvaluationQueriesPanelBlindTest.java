package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.DepartmentScopeFilter;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.access.domain.DepartmentScopePolicy;
import uz.hrlab.grading.access.domain.ProjectMembershipPolicy;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationCalibrationEventRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-A — END-TO-END proof of the blind-scoring rule (R-CRIT-1 / REQ-ISO-2 /
 * REQ-ISO-4) on EVERY read path of {@link EvaluationQueries}, with a REAL
 * {@link PanelBiasGuard} over a panel that is still COLLECTING. This complements
 * {@link PanelBiasGuardTest} (which unit-tests the guard in isolation) by proving
 * the guard is actually WIRED into the three read surfaces the live evaluation
 * screens use:
 *
 * <ol>
 *   <li><b>By-factor grid</b> ({@code listByFactor}) — a non-bypass caller is
 *       routed to the OWN-ONLY finder ({@code findForFactorGridOwnOnly}) so a peer
 *       never even sees evaluator B's row; the all-evaluators finder is never
 *       called. A CAMPAIGN_RESULTS_VIEW holder gets the full grid.</li>
 *   <li><b>Single-id scores</b> ({@code findScoresByEvaluationId}) — a peer
 *       reading evaluator B's collecting sheet → 404 ({@link
 *       TenantAccessDeniedException}) + ACCESS_DENIED_BY_ABAC, and the scores are
 *       never loaded. The owner reads their own.</li>
 *   <li><b>Single-sheet detail</b> ({@code findById}) — the endpoint behind
 *       EvaluationDetailsPage / MyEvaluationsPage deep-link: same blind — a peer
 *       is 404'd, the owner passes.</li>
 * </ol>
 *
 * EVALUATION_READ alone never lifts the blind (deny-by-default).
 */
@Tag("security")
@ExtendWith(MockitoExtension.class)
class EvaluationQueriesPanelBlindTest {

    @Mock EvaluationRepository evaluations;
    @Mock EvaluationScoreRepository scores;
    @Mock EvaluationCalibrationEventRepository calibrationEvents;
    @Mock FactorRepository factors;
    @Mock PositionRepository positions;
    @Mock DepartmentRepository departments;
    @Mock PanelRepository panels;
    @Mock AuditService audit;

    DepartmentScopeFilter departmentScopeFilter = new DepartmentScopeFilter();
    AbacGate abacGate;
    PanelBiasGuard panelBiasGuard;
    EvaluationQueries queries;

    UUID tenantId;
    UUID projectId;
    UUID panelId;
    UUID positionId;
    UUID factorId;
    UUID versionId;
    UUID evaluationId;
    UUID evaluatorA;   // the caller
    UUID evaluatorB;   // a peer whose sheet must stay blind
    Pageable pageable = PageRequest.of(0, 50);

    @BeforeEach
    void setUp() {
        abacGate = new AbacGate(
                List.of(new ProjectMembershipPolicy(), new DepartmentScopePolicy()), audit);
        panelBiasGuard = new PanelBiasGuard(panels, audit);
        queries = new EvaluationQueries(evaluations, scores, calibrationEvents,
                factors, positions, departments, departmentScopeFilter, abacGate, panelBiasGuard);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        panelId = UUID.randomUUID();
        positionId = UUID.randomUUID();
        factorId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        evaluationId = UUID.randomUUID();
        evaluatorA = UUID.randomUUID();
        evaluatorB = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() { TenantContextHolder.clear(); }

    // ====================================================== 1. BY-FACTOR GRID

    @Test
    void gridConfinesNonBypassCallerToOwnRowsViaOwnOnlyFinder() {
        setEvaluatorContext(evaluatorA, Set.of("EVALUATION_READ"));
        stubFactor();
        when(evaluations.findForFactorGridOwnOnly(
                eq(tenantId), eq(projectId), eq(versionId), any(), any(),
                eq(evaluatorA), any()))
                .thenReturn(new PageImpl<>(List.<EvaluationJpaEntity>of()));

        queries.listByFactor(projectId, factorId, null, null, pageable);

        // The grid is confined to the CALLER's own evaluations (own-only finder
        // bound to evaluatorA) — a peer's row can never surface.
        verify(evaluations).findForFactorGridOwnOnly(
                eq(tenantId), eq(projectId), eq(versionId), any(), any(),
                eq(evaluatorA), any());
        // The all-evaluators grid finder is NEVER used for a non-bypass caller.
        verify(evaluations, never())
                .findForFactorGrid(any(), any(), any(), any(), any(), any());
    }

    @Test
    void gridGivesFullViewToCampaignResultsViewHolder() {
        setEvaluatorContext(evaluatorA, Set.of("EVALUATION_READ", "CAMPAIGN_RESULTS_VIEW"));
        stubFactor();
        when(evaluations.findForFactorGrid(
                eq(tenantId), eq(projectId), eq(versionId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.<EvaluationJpaEntity>of()));

        queries.listByFactor(projectId, factorId, null, null, pageable);

        // A result-viewer lifts the blind: the FULL grid finder is used, the
        // own-only finder is not.
        verify(evaluations).findForFactorGrid(
                eq(tenantId), eq(projectId), eq(versionId), any(), any(), any());
        verify(evaluations, never()).findForFactorGridOwnOnly(
                any(), any(), any(), any(), any(), any(), any());
    }

    // ================================================ 2. SINGLE-ID SCORES READ

    @Test
    void scoresPeerOnForeignCollectingSheetIs404WithDenialAndNoScoreLoad() {
        setEvaluatorContext(evaluatorA, Set.of("EVALUATION_READ"));
        stubCollectingPanel();
        stubPositionInScope(); // tenant-wide caller passes the dept gate
        stubSheetOwnedBy(evaluatorB); // foreign sheet

        assertThatThrownBy(() -> queries.findScoresByEvaluationId(evaluationId))
                .isInstanceOf(TenantAccessDeniedException.class); // → 404, no reveal

        verify(scores, never()).findAllByTenantIdAndEvaluationId(any(), any());
        assertDenialAudited();
    }

    @Test
    void scoresOwnerOnOwnCollectingSheetIsAllowed() {
        setEvaluatorContext(evaluatorA, Set.of("EVALUATION_READ"));
        stubCollectingPanel();
        stubSheetOwnedBy(evaluatorA); // own sheet
        when(scores.findAllByTenantIdAndEvaluationId(tenantId, evaluationId))
                .thenReturn(List.of());

        assertThat(queries.findScoresByEvaluationId(evaluationId)).isEmpty();
        verify(scores).findAllByTenantIdAndEvaluationId(tenantId, evaluationId);
    }

    // ============================================== 3. SINGLE-SHEET DETAIL READ

    @Test
    void detailPeerOnForeignCollectingSheetIs404WithDenial() {
        setEvaluatorContext(evaluatorA, Set.of("EVALUATION_READ"));
        stubCollectingPanel();
        stubPositionInScope();
        stubSheetOwnedBy(evaluatorB);

        assertThatThrownBy(() -> queries.findById(evaluationId))
                .isInstanceOf(TenantAccessDeniedException.class);

        assertDenialAudited();
    }

    @Test
    void detailOwnerOnOwnCollectingSheetIsAllowed() {
        setEvaluatorContext(evaluatorA, Set.of("EVALUATION_READ"));
        stubCollectingPanel();
        EvaluationJpaEntity own = stubSheetOwnedBy(evaluatorA);

        assertThat(queries.findById(evaluationId)).isSameAs(own);
    }

    @Test
    void evaluationReadAloneDoesNotLiftTheBlindOnAnyPath() {
        // Deny-by-default: EVALUATION_READ is the ONLY permission and the peer is
        // still blocked on both single-id paths.
        setEvaluatorContext(evaluatorA, Set.of("EVALUATION_READ"));
        stubCollectingPanel();
        stubPositionInScope();
        stubSheetOwnedBy(evaluatorB);

        assertThatThrownBy(() -> queries.findById(evaluationId))
                .isInstanceOf(TenantAccessDeniedException.class);
        assertThatThrownBy(() -> queries.findScoresByEvaluationId(evaluationId))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void resultViewerPeerMayReadForeignCollectingSheet() {
        // The blind is lifted ONLY by CAMPAIGN_RESULTS_VIEW (HR director / PM / CEO).
        setEvaluatorContext(evaluatorA, Set.of("EVALUATION_READ", "CAMPAIGN_RESULTS_VIEW"));
        stubPositionInScope(); // tenant-wide caller passes the C-2 dept gate
        EvaluationJpaEntity foreign = stubSheetOwnedBy(evaluatorB);

        assertThatCode(() -> queries.findById(evaluationId)).doesNotThrowAnyException();
        assertThat(queries.findById(evaluationId)).isSameAs(foreign);
        // The bias guard's bypass short-circuits before the panel is consulted.
        verify(panels, never()).findByIdAndTenantId(any(), any());
    }

    // --------------------------------------------------------------- helpers

    private void stubFactor() {
        FactorJpaEntity factor = new FactorJpaEntity(
                factorId, tenantId, versionId, "F-1",
                BigDecimal.ONE, BigDecimal.TEN, 0, true);
        when(factors.findByIdAndTenantId(factorId, tenantId)).thenReturn(Optional.of(factor));
    }

    private void stubCollectingPanel() {
        EvaluationPanelJpaEntity panel = new EvaluationPanelJpaEntity(
                panelId, tenantId, projectId, positionId, versionId,
                EvaluationPanelStatus.AWAITING_EVALUATIONS, 3);
        lenient().when(panels.findByIdAndTenantId(panelId, tenantId))
                .thenReturn(Optional.of(panel));
    }

    /** Position resolved by the C-2 dept gate; a tenant-wide caller is always in-scope. */
    private void stubPositionInScope() {
        PositionJpaEntity p = new PositionJpaEntity(
                positionId, tenantId, projectId, UUID.randomUUID(), "P-001",
                Map.of("ru-RU", "Position"), null, null, null, null, PositionStatus.ACTIVE);
        lenient().when(positions.findByIdAndTenantId(positionId, tenantId))
                .thenReturn(Optional.of(p));
    }

    private EvaluationJpaEntity stubSheetOwnedBy(UUID owner) {
        EvaluationJpaEntity sheet = new EvaluationJpaEntity(
                evaluationId, tenantId, projectId, positionId, versionId,
                owner, EvaluationStatus.INCOMPLETE);
        sheet.setPanelId(panelId);
        when(evaluations.findByIdAndTenantId(evaluationId, tenantId))
                .thenReturn(Optional.of(sheet));
        return sheet;
    }

    private void assertDenialAudited() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, atLeastOnce()).record(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(e -> AuditAction.ACCESS_DENIED_BY_ABAC.equals(e.action()));
    }

    /**
     * Tenant-wide caller ({@code CLIENT_HR_SPECIALIST}) so the C-2 department gate
     * is satisfied and the test isolates the BIAS blind specifically. The blind
     * keys only on the {@code CAMPAIGN_RESULTS_VIEW} permission, never the role.
     */
    private void setEvaluatorContext(UUID userId, Set<String> permissions) {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of(RoleCodes.CLIENT_HR_SPECIALIST),
                permissions, Set.of(), false, "ru-RU"));
    }
}
