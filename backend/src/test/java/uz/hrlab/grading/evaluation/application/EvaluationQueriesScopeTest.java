package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationCalibrationEventRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E4-S2 list-filter behaviour PLUS C-2 single-id read-scope behaviour of
 * {@link EvaluationQueries}.
 *
 * <p>Evaluations inherit their department from their Position, so a scoped caller
 * routes list reads to {@code findInDepartments(...)} and single-id reads through
 * the shared {@link AbacGate#enforceCanReadPosition} (the same read path positions
 * use). The {@code AbacGate} is REAL here (real {@link ProjectMembershipPolicy} +
 * {@link DepartmentScopePolicy}) so the C-2 department gate is genuinely exercised.
 *
 * <h3>List (E4-S2)</h3>
 * <ul>
 *   <li>bypass role → existing tenant/project finders, unfiltered;</li>
 *   <li>department-scoped role with scope {D1} → {@code findInDepartments(...)}
 *       with exactly {D1};</li>
 *   <li>department-scoped role with EMPTY scope → ZERO rows, no finder call.</li>
 * </ul>
 *
 * <h3>Single-id reads (C-2 — intra-tenant IDOR fix)</h3>
 * For each of {@code findById} / {@code findScoresByEvaluationId} /
 * {@code findCalibrationHistory}: out-of-subtree → 404 + denial audit; in-subtree
 * → ok; bypass role → ok; empty-scope dept-scoped role → denied.
 */
@ExtendWith(MockitoExtension.class)
class EvaluationQueriesScopeTest {

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
    UUID d1;
    UUID inScopeDept;
    UUID outOfScopeDept;
    UUID evaluationId;
    UUID positionId;
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
        d1 = UUID.randomUUID();
        inScopeDept = UUID.randomUUID();
        outOfScopeDept = UUID.randomUUID();
        evaluationId = UUID.randomUUID();
        positionId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() { TenantContextHolder.clear(); }

    // ============================================================ LIST (E4-S2)

    @Test
    void bypassRoleUsesUnfilteredProjectFinder() {
        setContext(Set.of(RoleCodes.CLIENT_HR_DIRECTOR), Set.of());
        when(evaluations.findAllByTenantIdAndProjectId(eq(tenantId), eq(projectId), any()))
                .thenReturn(new PageImpl<>(List.<EvaluationJpaEntity>of()));

        queries.list(projectId, null, null, null, pageable);

        verify(evaluations).findAllByTenantIdAndProjectId(eq(tenantId), eq(projectId), any());
        verify(evaluations, never())
                .findInDepartments(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void departmentScopedRoleUsesScopedFinderWithExactInSet() {
        setContext(Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), Set.of(d1));
        when(evaluations.findInDepartments(
                eq(tenantId), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.<EvaluationJpaEntity>of()));

        queries.list(projectId, null, null, null, pageable);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> scopeCaptor =
                ArgumentCaptor.forClass(Collection.class);
        verify(evaluations).findInDepartments(
                eq(tenantId), eq(projectId), any(), any(), any(),
                scopeCaptor.capture(), any());
        assertThat(scopeCaptor.getValue()).containsExactly(d1);
        verify(evaluations, never())
                .findAllByTenantIdAndProjectId(any(), any(), any());
    }

    @Test
    void emptyScopeReturnsZeroRowsAndHitsNoFinder() {
        setContext(Set.of(RoleCodes.DEPARTMENT_MANAGER), Set.of());

        Page<?> result = queries.list(projectId, null, null, null, pageable);

        assertThat(result.getTotalElements()).isZero();
        verify(evaluations, never())
                .findInDepartments(any(), any(), any(), any(), any(), any(), any());
        verify(evaluations, never()).findAllByTenantIdAndProjectId(any(), any(), any());
        verify(evaluations, never()).findAllByTenantId(any(), any());
    }

    // ================================================= SINGLE-ID READS (C-2)

    @Test
    void findByIdOutOfSubtreeIsDeniedWithAuditAndNoReveal() {
        setExpertContext(Set.of(inScopeDept));
        stubEvaluation();
        stubPosition(outOfScopeDept);

        assertThatThrownBy(() -> queries.findById(evaluationId))
                .isInstanceOf(TenantAccessDeniedException.class); // → 404

        assertDenialAudited();
    }

    @Test
    void findByIdInSubtreeReturnsEvaluation() {
        setExpertContext(Set.of(inScopeDept));
        EvaluationJpaEntity evaluation = stubEvaluation();
        stubPosition(inScopeDept);

        assertThat(queries.findById(evaluationId)).isSameAs(evaluation);
    }

    @Test
    void findByIdBypassRoleReturnsEvaluationRegardlessOfDepartment() {
        setContext(Set.of(RoleCodes.CLIENT_HR_DIRECTOR), Set.of());
        EvaluationJpaEntity evaluation = stubEvaluation();
        stubPosition(outOfScopeDept);

        assertThat(queries.findById(evaluationId)).isSameAs(evaluation);
    }

    @Test
    void findByIdEmptyScopeDeptScopedRoleIsDenied() {
        setExpertContext(Set.of()); // dept-scoped role, no assignment → fail-closed
        stubEvaluation();
        stubPosition(inScopeDept);

        assertThatThrownBy(() -> queries.findById(evaluationId))
                .isInstanceOf(TenantAccessDeniedException.class);

        assertDenialAudited();
    }

    @Test
    void findScoresOutOfSubtreeIsDeniedBeforeLoadingScores() {
        setExpertContext(Set.of(inScopeDept));
        stubEvaluation();
        stubPosition(outOfScopeDept);

        assertThatThrownBy(() -> queries.findScoresByEvaluationId(evaluationId))
                .isInstanceOf(TenantAccessDeniedException.class);

        verify(scores, never()).findAllByTenantIdAndEvaluationId(any(), any());
        assertDenialAudited();
    }

    @Test
    void findScoresInSubtreeReturnsScores() {
        setExpertContext(Set.of(inScopeDept));
        stubEvaluation();
        stubPosition(inScopeDept);
        when(scores.findAllByTenantIdAndEvaluationId(tenantId, evaluationId))
                .thenReturn(List.of());

        assertThat(queries.findScoresByEvaluationId(evaluationId)).isEmpty();
        verify(scores).findAllByTenantIdAndEvaluationId(tenantId, evaluationId);
    }

    @Test
    void findCalibrationHistoryOutOfSubtreeIsDeniedBeforeLoadingEvents() {
        setExpertContext(Set.of(inScopeDept));
        stubEvaluation();
        stubPosition(outOfScopeDept);

        assertThatThrownBy(() -> queries.findCalibrationHistory(evaluationId))
                .isInstanceOf(TenantAccessDeniedException.class);

        verify(calibrationEvents, never())
                .findAllByTenantIdAndEvaluationIdOrderByDecidedAtDesc(any(), any());
        assertDenialAudited();
    }

    @Test
    void findCalibrationHistoryInSubtreeReturnsEvents() {
        setExpertContext(Set.of(inScopeDept));
        stubEvaluation();
        stubPosition(inScopeDept);
        when(calibrationEvents.findAllByTenantIdAndEvaluationIdOrderByDecidedAtDesc(
                tenantId, evaluationId)).thenReturn(List.of());

        assertThat(queries.findCalibrationHistory(evaluationId)).isEmpty();
        verify(calibrationEvents)
                .findAllByTenantIdAndEvaluationIdOrderByDecidedAtDesc(tenantId, evaluationId);
    }

    // --------------------------------------------------------------- helpers

    private EvaluationJpaEntity stubEvaluation() {
        EvaluationJpaEntity evaluation = new EvaluationJpaEntity(
                evaluationId, tenantId, projectId, positionId, UUID.randomUUID(),
                UUID.randomUUID(), EvaluationStatus.DRAFT);
        when(evaluations.findByIdAndTenantId(evaluationId, tenantId))
                .thenReturn(Optional.of(evaluation));
        return evaluation;
    }

    private void stubPosition(UUID departmentId) {
        PositionJpaEntity p = new PositionJpaEntity(
                positionId, tenantId, projectId, departmentId, "P-001",
                Map.of("ru-RU", "Position"), null, null, null, null, PositionStatus.ACTIVE);
        when(positions.findByIdAndTenantId(positionId, tenantId)).thenReturn(Optional.of(p));
    }

    private void assertDenialAudited() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, atLeastOnce()).record(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(e -> AuditAction.ACCESS_DENIED_BY_ABAC.equals(e.action()));
    }

    private void setContext(Set<String> roles, Set<UUID> deptScope) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(projectId), roles,
                Set.of("EVALUATION_READ"), deptScope, false, "ru-RU"));
    }

    private void setExpertContext(Set<UUID> deptScope) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(projectId),
                Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER),
                Set.of("EVALUATION_READ"), deptScope, false, "ru-RU"));
    }
}
