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
import uz.hrlab.grading.access.application.DepartmentScopeFilter;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationCalibrationEventRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E4-S2 — department-filter behaviour of {@link EvaluationQueries#list}.
 * Evaluations inherit their department from their Position, so a scoped caller
 * routes to {@code findInDepartments(...)} (position-department subquery).
 *
 * <ul>
 *   <li>bypass role → existing tenant/project finders, unfiltered;</li>
 *   <li>department-scoped role with scope {D1} → {@code findInDepartments(...)}
 *       with exactly {D1};</li>
 *   <li>department-scoped role with EMPTY scope → ZERO rows, no finder call.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EvaluationQueriesScopeTest {

    @Mock EvaluationRepository evaluations;
    @Mock EvaluationScoreRepository scores;
    @Mock EvaluationCalibrationEventRepository calibrationEvents;
    @Mock FactorRepository factors;
    @Mock PositionRepository positions;
    @Mock DepartmentRepository departments;

    DepartmentScopeFilter departmentScopeFilter = new DepartmentScopeFilter();

    EvaluationQueries queries;

    UUID tenantId;
    UUID projectId;
    UUID d1;
    Pageable pageable = PageRequest.of(0, 50);

    @BeforeEach
    void setUp() {
        queries = new EvaluationQueries(evaluations, scores, calibrationEvents,
                factors, positions, departments, departmentScopeFilter);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        d1 = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() { TenantContextHolder.clear(); }

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

    private void setContext(Set<String> roles, Set<UUID> deptScope) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(projectId), roles,
                Set.of("EVALUATION_READ"), deptScope, false, "ru-RU"));
    }
}
