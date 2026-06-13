package uz.hrlab.grading.position.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.DepartmentScopeFilter;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * E4-S2 — department-filter behaviour of {@link FindPositionQuery#list}.
 *
 * <ul>
 *   <li>bypass role (CLIENT_HR_DIRECTOR) → unfiltered {@code search(...)}, sees
 *       all departments;</li>
 *   <li>department-scoped role (DEPARTMENT_MANAGER) with scope {D1} →
 *       {@code searchInDepartments(...)} with exactly {D1} as the IN-set;</li>
 *   <li>department-scoped role with EMPTY scope → ZERO rows and NO repository
 *       call (fail-closed).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FindPositionQueryScopeTest {

    @Mock PositionRepository positions;
    @Mock DepartmentRepository departments;
    @Mock AbacGate abacGate;

    // Real filter — the role classification is what we are exercising.
    DepartmentScopeFilter departmentScopeFilter = new DepartmentScopeFilter();

    FindPositionQuery query;

    UUID tenantId;
    UUID projectId;
    UUID d1;

    @BeforeEach
    void setUp() {
        query = new FindPositionQuery(positions, departments, abacGate, departmentScopeFilter);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        d1 = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() { TenantContextHolder.clear(); }

    @Test
    void bypassRoleUsesUnfilteredSearch() {
        setContext(Set.of(RoleCodes.CLIENT_HR_DIRECTOR), Set.of());
        when(positions.search(eq(tenantId), eq(projectId), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(position(d1))));

        query.list(projectId, null, null, null, 0, 50);

        verify(positions).search(eq(tenantId), eq(projectId), any(), any(), any(), any());
        verify(positions, never())
                .searchInDepartments(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void departmentScopedRoleUsesScopedSearchWithExactInSet() {
        setContext(Set.of(RoleCodes.DEPARTMENT_MANAGER), Set.of(d1));
        when(positions.searchInDepartments(
                eq(tenantId), eq(projectId), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(position(d1))));

        query.list(projectId, null, null, null, 0, 50);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> scopeCaptor =
                ArgumentCaptor.forClass(Collection.class);
        verify(positions).searchInDepartments(
                eq(tenantId), eq(projectId), any(), any(), any(),
                scopeCaptor.capture(), any());
        assertThat(scopeCaptor.getValue()).containsExactly(d1);
        verify(positions, never()).search(any(), any(), any(), any(), any(), any());
    }

    @Test
    void emptyScopeReturnsZeroRowsAndHitsNoFinder() {
        setContext(Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), Set.of());

        Page<?> result = query.list(projectId, null, null, null, 0, 50);

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
        // Fail-closed: neither finder is invoked — no count/existence leak.
        verify(positions, never()).search(any(), any(), any(), any(), any(), any());
        verify(positions, never())
                .searchInDepartments(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void includeSubtreeExpandsRequestedDepartmentViaOneClosureCall() {
        // T4 — includeSubtree expands the requested dept to its subtree via the
        // EXISTING findSubtreeIds (one call) and queries searchInDepartments with
        // the subtree set (departmentId pinned to null so it is not re-confined).
        setContext(Set.of(RoleCodes.CLIENT_HR_DIRECTOR), Set.of()); // unscoped bypass
        UUID childA = UUID.randomUUID();
        UUID childB = UUID.randomUUID();
        when(departments.findSubtreeIds(List.of(d1), tenantId))
                .thenReturn(List.of(d1, childA, childB));
        when(positions.searchInDepartments(
                eq(tenantId), eq(projectId), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(position(childA))));

        query.list(projectId, d1, true, null, null, 0, 50);

        verify(departments, times(1)).findSubtreeIds(List.of(d1), tenantId);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> scopeCaptor = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<UUID> deptCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(positions).searchInDepartments(
                eq(tenantId), eq(projectId), deptCaptor.capture(), any(), any(),
                scopeCaptor.capture(), any());
        assertThat(scopeCaptor.getValue()).containsExactlyInAnyOrder(d1, childA, childB);
        assertThat(deptCaptor.getValue()).isNull(); // IN-set confines; no re-pin
        verify(positions, never()).search(any(), any(), any(), any(), any(), any());
    }

    @Test
    void includeSubtreeFalseLeavesExactDepartmentBehaviourUnchanged() {
        setContext(Set.of(RoleCodes.CLIENT_HR_DIRECTOR), Set.of());
        when(positions.search(eq(tenantId), eq(projectId), eq(d1), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(position(d1))));

        query.list(projectId, d1, false, null, null, 0, 50);

        verify(positions).search(eq(tenantId), eq(projectId), eq(d1), any(), any(), any());
        verify(departments, never()).findSubtreeIds(any(), any());
        verify(positions, never())
                .searchInDepartments(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void includeSubtreeIntersectsWithDepartmentScopeFailClosed() {
        // Department-scoped caller requesting a subtree outside their scope → zero
        // rows (intersection empty), never a widen.
        UUID inScope = UUID.randomUUID();
        setContext(Set.of(RoleCodes.DEPARTMENT_MANAGER), Set.of(inScope));
        when(departments.findSubtreeIds(List.of(d1), tenantId))
                .thenReturn(List.of(d1, UUID.randomUUID())); // none in scope

        Page<?> result = query.list(projectId, d1, true, null, null, 0, 50);

        assertThat(result.getTotalElements()).isZero();
        verify(positions, never())
                .searchInDepartments(any(), any(), any(), any(), any(), any(), any());
        verify(positions, never()).search(any(), any(), any(), any(), any(), any());
    }

    private void setContext(Set<String> roles, Set<UUID> deptScope) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(projectId), roles,
                Set.of(), deptScope, false, "ru-RU"));
    }

    private PositionJpaEntity position(UUID departmentId) {
        return new PositionJpaEntity(
                UUID.randomUUID(), tenantId, projectId, departmentId, "P-001",
                Map.of("ru-RU", "Position"), null, null, null, null, PositionStatus.ACTIVE);
    }
}
