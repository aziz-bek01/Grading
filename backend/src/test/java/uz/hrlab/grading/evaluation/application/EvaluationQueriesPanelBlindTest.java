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
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationCalibrationEventRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
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
 * P0-A / Defect-2 — END-TO-END proof of the blind-scoring rule (R-CRIT-1 /
 * REQ-ISO-2 / REQ-ISO-4) on EVERY read path of {@link EvaluationQueries}, with a
 * REAL {@link PanelBiasGuard}. This complements {@link PanelBiasGuardTest} (which
 * unit-tests the guard in isolation) by proving the guard is actually WIRED into
 * the read surfaces the live evaluation screens use:
 *
 * <ol>
 *   <li><b>By-factor grid</b> ({@code listByFactor}) — a non-oversight caller is
 *       routed to the OWN-ONLY finder ({@code findForFactorGridOwnOnly}) so a peer
 *       never even sees evaluator B's row; the all-evaluators finder is never
 *       called. An HRLab oversight role gets the full grid.</li>
 *   <li><b>Single-id scores</b> ({@code findScoresByEvaluationId}) — a peer
 *       reading evaluator B's panel sheet → 404 ({@link
 *       TenantAccessDeniedException}) + ACCESS_DENIED_BY_ABAC, scores never loaded.
 *       The owner reads their own.</li>
 *   <li><b>Single-sheet detail</b> ({@code findById}) and <b>calibration
 *       history</b> ({@code findCalibrationHistory}) — same blind.</li>
 * </ol>
 *
 * Defect-2: neither EVALUATION_READ nor CAMPAIGN_RESULTS_VIEW lifts the per-sheet
 * blind; ONLY an HRLab oversight role does.
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
    @Mock AuditService audit;

    DepartmentScopeFilter departmentScopeFilter = new DepartmentScopeFilter();
    AbacGate abacGate;
    PanelBiasGuard panelBiasGuard;
    EvaluationQueries queries;

    UUID tenantId;
    UUID projectId;
    UUID panelId;
    UUID positionId;
    UUID departmentId;
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
        panelBiasGuard = new PanelBiasGuard(audit, evaluations);
        queries = new EvaluationQueries(evaluations, scores, calibrationEvents,
                factors, positions, departments, departmentScopeFilter, abacGate, panelBiasGuard);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        panelId = UUID.randomUUID();
        positionId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
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
    void gridConfinesNonOversightCallerToOwnRowsViaOwnOnlyFinder() {
        // CLIENT_HR_SPECIALIST is neither oversight nor department-scoped, so the
        // grid takes the non-department own-only finder confined to the caller.
        setContext(evaluatorA, Set.of(RoleCodes.CLIENT_HR_SPECIALIST), "EVALUATION_READ");
        stubFactor();
        when(evaluations.findForFactorGridOwnOnly(
                eq(tenantId), eq(projectId), eq(versionId), any(), any(),
                eq(evaluatorA), any()))
                .thenReturn(new PageImpl<>(List.<EvaluationJpaEntity>of()));

        queries.listByFactor(projectId, factorId, null, null, pageable);

        verify(evaluations).findForFactorGridOwnOnly(
                eq(tenantId), eq(projectId), eq(versionId), any(), any(),
                eq(evaluatorA), any());
        verify(evaluations, never())
                .findForFactorGrid(any(), any(), any(), any(), any(), any());
    }

    @Test
    void gridConfinesCampaignResultsViewPeerToOwnRows() {
        // Defect-2: CAMPAIGN_RESULTS_VIEW no longer lifts the grid blind. A
        // CLIENT_HR_SPECIALIST peer holding CAMPAIGN_RESULTS_VIEW is still confined
        // to their OWN rows (non-department own-only finder).
        setContext(evaluatorA, Set.of(RoleCodes.CLIENT_HR_SPECIALIST),
                "EVALUATION_READ", "CAMPAIGN_RESULTS_VIEW");
        stubFactor();
        when(evaluations.findForFactorGridOwnOnly(
                eq(tenantId), eq(projectId), eq(versionId), any(), any(),
                eq(evaluatorA), any()))
                .thenReturn(new PageImpl<>(List.<EvaluationJpaEntity>of()));

        queries.listByFactor(projectId, factorId, null, null, pageable);

        verify(evaluations).findForFactorGridOwnOnly(
                eq(tenantId), eq(projectId), eq(versionId), any(), any(),
                eq(evaluatorA), any());
        verify(evaluations, never())
                .findForFactorGrid(any(), any(), any(), any(), any(), any());
    }

    @Test
    void gridGivesFullViewToPureOverseer() {
        // Defect-3: a PURE overseer — oversight role with NO own evaluation in this
        // (tenant, project, version) scope — keeps the full-grid calibration view.
        setContext(evaluatorA, Set.of(RoleCodes.HRLAB_CONSULTANT), "EVALUATION_READ");
        stubFactor();
        when(evaluations.existsByTenantIdAndProjectIdAndMethodologyVersionIdAndEvaluatorUserId(
                tenantId, projectId, versionId, evaluatorA)).thenReturn(false);
        when(evaluations.findForFactorGrid(
                eq(tenantId), eq(projectId), eq(versionId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.<EvaluationJpaEntity>of()));

        queries.listByFactor(projectId, factorId, null, null, pageable);

        verify(evaluations).findForFactorGrid(
                eq(tenantId), eq(projectId), eq(versionId), any(), any(), any());
        verify(evaluations, never()).findForFactorGridOwnOnly(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void gridConfinesOversightHolderWhoIsAMemberInScopeToOwnRows() {
        // Defect-3 core fix: an oversight-role holder (HRLAB_SUPER_ADMIN — the "asr"
        // repro) who is ALSO a member/evaluator in THIS grid's scope (he has his own
        // evaluations in this version) is a PEER → confined to OWN rows via the
        // own-only finder. The full-grid finder is never called, so he never sees a
        // co-evaluator's in-progress level selection.
        setContext(evaluatorA, Set.of(RoleCodes.HRLAB_SUPER_ADMIN), "EVALUATION_READ");
        stubFactor();
        when(evaluations.existsByTenantIdAndProjectIdAndMethodologyVersionIdAndEvaluatorUserId(
                tenantId, projectId, versionId, evaluatorA)).thenReturn(true);
        when(evaluations.findForFactorGridOwnOnly(
                eq(tenantId), eq(projectId), eq(versionId), any(), any(),
                eq(evaluatorA), any()))
                .thenReturn(new PageImpl<>(List.<EvaluationJpaEntity>of()));

        queries.listByFactor(projectId, factorId, null, null, pageable);

        verify(evaluations).findForFactorGridOwnOnly(
                eq(tenantId), eq(projectId), eq(versionId), any(), any(),
                eq(evaluatorA), any());
        verify(evaluations, never())
                .findForFactorGrid(any(), any(), any(), any(), any(), any());
    }

    // ================================================ 2. SINGLE-ID SCORES READ

    @Test
    void scoresPeerOnForeignPanelSheetIs404WithDenialAndNoScoreLoad() {
        setContext(evaluatorA, Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), "EVALUATION_READ");
        stubPositionInScope(); // tenant-wide caller passes the dept gate
        stubSheetOwnedBy(evaluatorB); // foreign sheet

        assertThatThrownBy(() -> queries.findScoresByEvaluationId(evaluationId))
                .isInstanceOf(TenantAccessDeniedException.class); // → 404, no reveal

        verify(scores, never()).findAllByTenantIdAndEvaluationId(any(), any());
        assertDenialAudited();
    }

    @Test
    void scoresCampaignResultsViewPeerOnForeignPanelSheetIs404() {
        // Defect-2 core repro: a peer holding CAMPAIGN_RESULTS_VIEW (department
        // director) reading another evaluator's panel scores → 404, not 200.
        setContext(evaluatorA, Set.of(RoleCodes.DEPARTMENT_MANAGER),
                "EVALUATION_READ", "CAMPAIGN_RESULTS_VIEW");
        stubPositionInScope();
        stubSheetOwnedBy(evaluatorB);

        assertThatThrownBy(() -> queries.findScoresByEvaluationId(evaluationId))
                .isInstanceOf(TenantAccessDeniedException.class);
        verify(scores, never()).findAllByTenantIdAndEvaluationId(any(), any());
        assertDenialAudited();
    }

    @Test
    void scoresOwnerOnOwnPanelSheetIsAllowed() {
        setContext(evaluatorA, Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), "EVALUATION_READ");
        stubSheetOwnedBy(evaluatorA); // own sheet
        when(scores.findAllByTenantIdAndEvaluationId(tenantId, evaluationId))
                .thenReturn(List.of());

        assertThat(queries.findScoresByEvaluationId(evaluationId)).isEmpty();
        verify(scores).findAllByTenantIdAndEvaluationId(tenantId, evaluationId);
    }

    @Test
    void scoresPureOverseerOnForeignPanelSheetIsAllowed() {
        // PURE overseer (oversight role, NOT a member of this panel) keeps the
        // per-sheet calibration bypass.
        setContext(evaluatorA, Set.of(RoleCodes.HRLAB_CONSULTANT), "EVALUATION_READ");
        stubPositionInScope();
        stubSheetOwnedBy(evaluatorB);
        when(evaluations.existsByTenantIdAndPanelIdAndEvaluatorUserId(tenantId, panelId, evaluatorA))
                .thenReturn(false);
        when(scores.findAllByTenantIdAndEvaluationId(tenantId, evaluationId))
                .thenReturn(List.of());

        assertThat(queries.findScoresByEvaluationId(evaluationId)).isEmpty();
        verify(scores).findAllByTenantIdAndEvaluationId(tenantId, evaluationId);
    }

    @Test
    void scoresOversightHolderWhoIsAPanelMemberOnForeignSheetIs404() {
        // Defect-3 single-sheet consistency: an oversight-role holder who is ALSO a
        // member/evaluator of this panel is a PEER → blind to a co-evaluator's
        // scores → 404 + denial, scores never loaded.
        setContext(evaluatorA, Set.of(RoleCodes.HRLAB_SUPER_ADMIN), "EVALUATION_READ");
        stubPositionInScope();
        stubSheetOwnedBy(evaluatorB);
        when(evaluations.existsByTenantIdAndPanelIdAndEvaluatorUserId(tenantId, panelId, evaluatorA))
                .thenReturn(true);

        assertThatThrownBy(() -> queries.findScoresByEvaluationId(evaluationId))
                .isInstanceOf(TenantAccessDeniedException.class);
        verify(scores, never()).findAllByTenantIdAndEvaluationId(any(), any());
        assertDenialAudited();
    }

    // ============================================== 3. SINGLE-SHEET DETAIL READ

    @Test
    void detailPeerOnForeignPanelSheetIs404WithDenial() {
        setContext(evaluatorA, Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), "EVALUATION_READ");
        stubPositionInScope();
        stubSheetOwnedBy(evaluatorB);

        assertThatThrownBy(() -> queries.findById(evaluationId))
                .isInstanceOf(TenantAccessDeniedException.class);

        assertDenialAudited();
    }

    @Test
    void detailOwnerOnOwnPanelSheetIsAllowed() {
        setContext(evaluatorA, Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), "EVALUATION_READ");
        EvaluationJpaEntity own = stubSheetOwnedBy(evaluatorA);

        assertThat(queries.findById(evaluationId)).isSameAs(own);
    }

    @Test
    void detailPureOverseerOnForeignPanelSheetIsAllowed() {
        // PURE overseer (no own evaluation in this panel) keeps the bypass.
        setContext(evaluatorA, Set.of(RoleCodes.HRLAB_SUPER_ADMIN), "EVALUATION_READ");
        stubPositionInScope();
        EvaluationJpaEntity foreign = stubSheetOwnedBy(evaluatorB);
        when(evaluations.existsByTenantIdAndPanelIdAndEvaluatorUserId(tenantId, panelId, evaluatorA))
                .thenReturn(false);

        assertThat(queries.findById(evaluationId)).isSameAs(foreign);
    }

    @Test
    void detailOversightHolderWhoIsAPanelMemberOnForeignSheetIs404() {
        // Defect-3 single-sheet consistency on the detail read.
        setContext(evaluatorA, Set.of(RoleCodes.HRLAB_SUPER_ADMIN), "EVALUATION_READ");
        stubPositionInScope();
        stubSheetOwnedBy(evaluatorB);
        when(evaluations.existsByTenantIdAndPanelIdAndEvaluatorUserId(tenantId, panelId, evaluatorA))
                .thenReturn(true);

        assertThatThrownBy(() -> queries.findById(evaluationId))
                .isInstanceOf(TenantAccessDeniedException.class);
        assertDenialAudited();
    }

    @Test
    void detailOversightMemberStillReadsOwnSheet() {
        // The owner short-circuit precedes the bypass/membership logic: an
        // oversight-member reading their OWN sheet is always allowed.
        setContext(evaluatorA, Set.of(RoleCodes.HRLAB_SUPER_ADMIN), "EVALUATION_READ");
        EvaluationJpaEntity own = stubSheetOwnedBy(evaluatorA);

        assertThat(queries.findById(evaluationId)).isSameAs(own);
    }

    // ============================================== 4. CALIBRATION HISTORY READ

    @Test
    void calibrationHistoryPeerOnForeignPanelSheetIs404WithDenial() {
        setContext(evaluatorA, Set.of(RoleCodes.DEPARTMENT_MANAGER),
                "EVALUATION_READ", "CAMPAIGN_RESULTS_VIEW");
        stubPositionInScope();
        stubSheetOwnedBy(evaluatorB);

        assertThatThrownBy(() -> queries.findCalibrationHistory(evaluationId))
                .isInstanceOf(TenantAccessDeniedException.class);
        verify(calibrationEvents, never())
                .findAllByTenantIdAndEvaluationIdOrderByDecidedAtDesc(any(), any());
        assertDenialAudited();
    }

    @Test
    void calibrationHistoryOwnerOnOwnPanelSheetIsAllowed() {
        setContext(evaluatorA, Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), "EVALUATION_READ");
        stubSheetOwnedBy(evaluatorA);
        when(calibrationEvents.findAllByTenantIdAndEvaluationIdOrderByDecidedAtDesc(tenantId, evaluationId))
                .thenReturn(List.of());

        assertThat(queries.findCalibrationHistory(evaluationId)).isEmpty();
        verify(calibrationEvents).findAllByTenantIdAndEvaluationIdOrderByDecidedAtDesc(tenantId, evaluationId);
    }

    @Test
    void neitherReadNorCampaignResultsViewLiftsTheBlindOnAnyPath() {
        // Deny-by-default: a peer with EVALUATION_READ + CAMPAIGN_RESULTS_VIEW is
        // still blocked on every per-sheet path.
        setContext(evaluatorA, Set.of(RoleCodes.DEPARTMENT_MANAGER),
                "EVALUATION_READ", "CAMPAIGN_RESULTS_VIEW");
        stubPositionInScope();
        stubSheetOwnedBy(evaluatorB);

        assertThatThrownBy(() -> queries.findById(evaluationId))
                .isInstanceOf(TenantAccessDeniedException.class);
        assertThatThrownBy(() -> queries.findScoresByEvaluationId(evaluationId))
                .isInstanceOf(TenantAccessDeniedException.class);
        assertThatThrownBy(() -> queries.findCalibrationHistory(evaluationId))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    // --------------------------------------------------------------- helpers

    private void stubFactor() {
        FactorJpaEntity factor = new FactorJpaEntity(
                factorId, tenantId, versionId, "F-1",
                BigDecimal.ONE, BigDecimal.TEN, 0, true);
        when(factors.findByIdAndTenantId(factorId, tenantId)).thenReturn(Optional.of(factor));
    }

    /**
     * Position resolved by the C-2 dept gate. The position lives in {@code
     * departmentId}, which the caller's department scope INCLUDES (see
     * {@link #setContext}). This makes the C-2 department gate PERMIT for a
     * department-scoped peer (the realistic Defect-2 repro: a department director
     * who CAN see the position's department) so the test isolates the BIAS blind
     * as the decisive blocker — not the department gate.
     */
    private void stubPositionInScope() {
        PositionJpaEntity p = new PositionJpaEntity(
                positionId, tenantId, projectId, departmentId, "P-001",
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
     * Build a caller context. The role set drives both the ABAC department/project
     * gate AND the new role-based bias bypass. The caller is a project member
     * (project in {@code projectIds}) and their department scope INCLUDES the
     * position's {@code departmentId}, so the C-2 department gate PERMITs and the
     * test isolates the BIAS blind specifically.
     */
    private void setContext(UUID userId, Set<String> roles, String... permissions) {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId), roles,
                Set.of(permissions), Set.of(departmentId), false, "ru-RU"));
    }
}
