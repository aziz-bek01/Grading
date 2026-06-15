package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.DepartmentScopeFilter;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.access.domain.DepartmentScopePolicy;
import uz.hrlab.grading.access.domain.ProjectMembershipPolicy;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.evaluation.api.EvaluationByFactorRow;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression for the "mixed methodology rows" defect: the by-factor K-sheet
 * ({@code groupBy=factor}) MUST be strictly scoped to ONE methodology version —
 * the version the requested factor belongs to. A factor belongs to exactly one
 * methodology version, so {@link EvaluationQueries#listByFactor} derives the
 * version from {@code factorId} server-side and confines the grid to evaluations
 * of that same version. Evaluations of OTHER methodologies (e.g. a 12-factor
 * HR-Lab methodology under an 8-factor methodology's factor tab) are excluded.
 *
 * <p>These are unit tests over a mocked repository: they prove the query layer
 * passes the DERIVED version to the (already version-filtered) finder and never
 * the wrong / no version — i.e. the filter is wired, not bypassed.
 */
@ExtendWith(MockitoExtension.class)
class EvaluationQueriesByFactorVersionScopeTest {

    @Mock EvaluationRepository evaluations;
    @Mock EvaluationScoreRepository scores;
    @Mock EvaluationCalibrationEventRepository calibrationEvents;
    @Mock FactorRepository factors;
    @Mock PositionRepository positions;
    @Mock DepartmentRepository departments;
    @Mock AuditService audit;

    DepartmentScopeFilter departmentScopeFilter = new DepartmentScopeFilter();
    AbacGate abacGate;
    EvaluationQueries queries;

    UUID tenantId;
    UUID projectId;
    UUID factorId;
    UUID factorVersionId;      // methodology version the factor belongs to
    UUID otherVersionId;       // a DIFFERENT methodology version in the same project
    UUID positionId;
    UUID departmentId;
    Pageable pageable = PageRequest.of(0, 50);

    @BeforeEach
    void setUp() {
        abacGate = new AbacGate(
                List.of(new ProjectMembershipPolicy(), new DepartmentScopePolicy()), audit);
        var panelBiasGuard = new uz.hrlab.grading.evaluation.application.PanelBiasGuard(
                org.mockito.Mockito.mock(
                        uz.hrlab.grading.evaluation.infrastructure.PanelRepository.class),
                audit);
        queries = new EvaluationQueries(evaluations, scores, calibrationEvents,
                factors, positions, departments, departmentScopeFilter, abacGate, panelBiasGuard);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        factorId = UUID.randomUUID();
        factorVersionId = UUID.randomUUID();
        otherVersionId = UUID.randomUUID();
        positionId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() { TenantContextHolder.clear(); }

    @Test
    void listByFactorPassesFactorsOwnVersionToTheGridFinder() {
        setBypassContext();
        stubFactor(factorVersionId);
        when(evaluations.findForFactorGrid(
                eq(tenantId), eq(projectId), eq(factorVersionId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.<EvaluationJpaEntity>of()));

        queries.listByFactor(projectId, factorId, null, null, pageable);

        // The grid is filtered by the SELECTED factor's OWN methodology version,
        // never another version and never an unscoped read.
        verify(evaluations).findForFactorGrid(
                eq(tenantId), eq(projectId), eq(factorVersionId), any(), any(), any());
        // The other methodology version is never used as the grid scope.
        verify(evaluations, never()).findForFactorGrid(
                any(), any(), eq(otherVersionId), any(), any(), any());
    }

    @Test
    void listByFactorExcludesEvaluationsOfAnotherMethodologyVersion() {
        setBypassContext();
        stubFactor(factorVersionId);
        // The finder is version-filtered at the DB level. The mock honours the
        // contract: ONLY same-version evaluations are returned; an evaluation of
        // otherVersionId is NOT in the result because it would be filtered out.
        EvaluationJpaEntity sameVersionEval = new EvaluationJpaEntity(
                UUID.randomUUID(), tenantId, projectId, positionId, factorVersionId,
                UUID.randomUUID(), EvaluationStatus.DRAFT);
        when(evaluations.findForFactorGrid(
                eq(tenantId), eq(projectId), eq(factorVersionId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sameVersionEval)));
        stubPosition();
        // PERF (P1) — scores are now batch-loaded for the whole page in ONE call.
        when(scores.findAllByTenantIdAndEvaluationIdIn(eq(tenantId), any()))
                .thenReturn(List.of());
        when(factors.findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(
                tenantId, factorVersionId)).thenReturn(List.of());

        Page<EvaluationByFactorRow> result =
                queries.listByFactor(projectId, factorId, null, null, pageable);

        // Every returned row belongs to an evaluation of the factor's own version.
        assertThat(result.getContent())
                .extracting(EvaluationByFactorRow::evaluationId)
                .containsExactly(sameVersionEval.getId());
        // Crucially, the finder was called with the factor's version — so the DB
        // never even returns the other-version evaluation.
        verify(evaluations).findForFactorGrid(
                eq(tenantId), eq(projectId), eq(factorVersionId), any(), any(), any());
    }

    @Test
    void departmentScopedCallerAlsoScopesGridToFactorsVersion() {
        setScopedContext(Set.of(departmentId));
        stubFactor(factorVersionId);
        when(evaluations.findForFactorGridInDepartments(
                eq(tenantId), eq(projectId), eq(factorVersionId), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.<EvaluationJpaEntity>of()));

        queries.listByFactor(projectId, factorId, null, null, pageable);

        verify(evaluations).findForFactorGridInDepartments(
                eq(tenantId), eq(projectId), eq(factorVersionId), any(), any(), any(), any());
        verify(evaluations, never())
                .findForFactorGrid(any(), any(), any(), any(), any(), any());
    }

    @Test
    void foreignTenantFactorIsRejectedBeforeAnyGridRead() {
        setBypassContext();
        when(factors.findByIdAndTenantId(factorId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queries.listByFactor(projectId, factorId, null, null, pageable))
                .isInstanceOf(TenantAccessDeniedException.class);

        verify(evaluations, never()).findForFactorGrid(any(), any(), any(), any(), any(), any());
        verify(evaluations, never())
                .findForFactorGridInDepartments(any(), any(), any(), any(), any(), any(), any());
    }

    // --------------------------------------------------------------- helpers

    private void stubFactor(UUID methodologyVersionId) {
        FactorJpaEntity factor = new FactorJpaEntity(
                factorId, tenantId, methodologyVersionId, "F-1",
                BigDecimal.ONE, BigDecimal.TEN, 0, true);
        when(factors.findByIdAndTenantId(factorId, tenantId)).thenReturn(Optional.of(factor));
    }

    private void stubPosition() {
        PositionJpaEntity p = new PositionJpaEntity(
                positionId, tenantId, projectId, departmentId, "P-001",
                Map.of("ru-RU", "Position"), null, null, null, null, PositionStatus.ACTIVE);
        // PERF (P1) — positions/departments are now batch-loaded for the whole
        // page in ONE tenant-scoped query each (findAllByTenantIdAndIdIn), not a
        // per-row findByIdAndTenantId. Stub the batch finders accordingly.
        when(positions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(p));
        when(departments.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of());
    }

    private void setBypassContext() {
        // CAMPAIGN_RESULTS_VIEW = a result-viewer (HR director) — sees the FULL
        // K-sheet grid (the bias blind is lifted), so the existing
        // findForFactorGrid assertions still hold under MVP2 BE-11.
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(projectId),
                Set.of(RoleCodes.CLIENT_HR_DIRECTOR),
                Set.of("EVALUATION_READ", "CAMPAIGN_RESULTS_VIEW"), Set.of(), false, "ru-RU"));
    }

    private void setScopedContext(Set<UUID> deptScope) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(projectId),
                Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER),
                Set.of("EVALUATION_READ", "CAMPAIGN_RESULTS_VIEW"), deptScope, false, "ru-RU"));
    }
}
