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
    @Mock AbacGate abacGate;

    // Real filter — the role classification is what we are exercising.
    DepartmentScopeFilter departmentScopeFilter = new DepartmentScopeFilter();

    FindPositionQuery query;

    UUID tenantId;
    UUID projectId;
    UUID d1;

    @BeforeEach
    void setUp() {
        query = new FindPositionQuery(positions, abacGate, departmentScopeFilter);
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
