package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
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
import uz.hrlab.grading.access.application.ActorNameResolver;
import uz.hrlab.grading.access.application.DepartmentScopeFilter;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.access.infrastructure.UserDepartmentScopeRepository;
import uz.hrlab.grading.evaluation.api.PanelResponse;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentRepository;
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

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EPIC-001 — unit test for the OBJECT-LEVEL ABAC scoping applied to the org-wide
 * panel list ({@link PanelQueries#list}). This mirrors {@code DepartmentScopeFilterTest}
 * one layer up: instead of asserting the filter's raw decision, it asserts that
 * {@code list()} ROUTES that decision correctly for every caller class and every
 * param combo, using the SHARED {@link DepartmentScopeFilter} (the same mechanism
 * {@code getPanelDetail} enforces per-row and {@code FindPositionQuery} /
 * {@code EvaluationQueries} use for their lists):
 *
 * <ul>
 *   <li>tenant-wide bypass (CEO / HR Director) → UNFILTERED tenant finders;
 *       {@code findInDepartments} is never touched (CEO overview preserved);</li>
 *   <li>department-scoped role with a scope → {@code findInDepartments} is invoked
 *       with EXACTLY the caller's {@code departmentScope}, for every param combo
 *       (no filter / status pull / project scope / position probe);</li>
 *   <li>department-scoped role with an EMPTY or null scope → ZERO rows, no DB
 *       finder is ever called (fail-closed).</li>
 * </ul>
 *
 * <p>The routing (not the mapping) is under test, so every finder returns an empty
 * page and assertions are by {@code verify} + argument capture.
 */
@Tag("security")
@Tag("abac")
@ExtendWith(MockitoExtension.class)
class PanelQueriesListScopeTest {

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
    UUID deptA;
    UUID deptB;
    final Pageable pageable = PageRequest.of(0, 50);

    @BeforeEach
    void setUp() {
        // Real (stateless) filter — the SINGLE decision point reused from the
        // position / evaluation list queries; no second filtering approach.
        queries = new PanelQueries(panels, assignments, averages, evaluations, scores,
                positions, factors, actorNames, abacGate, departments, departmentScopes,
                new DepartmentScopeFilter(), new DepartmentHierarchyResolver(departments));
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        positionId = UUID.randomUUID();
        deptA = UUID.randomUUID();
        deptB = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() { TenantContextHolder.clear(); }

    // ------------------------------------------------------------- bypass (CEO)

    @Test
    void ceoBypassListsWholeTenantAndNeverScopes() {
        // REQ-CEO regression lock (unit level): the org-wide approver reads EVERY
        // department's panels — the department-scoped finder must NOT be engaged.
        setContext(Set.of(RoleCodes.CLIENT_CEO), null,
                Set.of("EVALUATION_READ", "EVALUATION_PANEL_APPROVE"));
        when(panels.findAllByTenantId(eq(tenantId), any())).thenReturn(emptyPage());

        queries.list(null, null, null, pageable);

        verify(panels).findAllByTenantId(eq(tenantId), any());
        verify(panels, never()).findInDepartments(any(), any(), any(), any(), any(), any());
    }

    @Test
    void hrDirectorBypassStatusPullUsesTenantWideStatusInNotScoped() {
        setContext(Set.of(RoleCodes.CLIENT_HR_DIRECTOR), Set.of(),
                Set.of("EVALUATION_READ"));
        when(panels.findAllByTenantIdAndStatusIn(eq(tenantId), any(), any()))
                .thenReturn(emptyPage());

        queries.list(null, null, List.of(EvaluationPanelStatus.SUBMITTED), pageable);

        verify(panels).findAllByTenantIdAndStatusIn(eq(tenantId), any(), any());
        verify(panels, never()).findInDepartments(any(), any(), any(), any(), any(), any());
    }

    // ------------------------------------------- department-scoped: subset paths

    @Test
    void departmentManagerNoFilterScopesToAssignedSubtreeWithFullStatusSet() {
        setContext(Set.of(RoleCodes.DEPARTMENT_MANAGER), Set.of(deptA),
                Set.of("EVALUATION_READ"));
        when(panels.findInDepartments(eq(tenantId), any(), any(), any(), any(), any()))
                .thenReturn(emptyPage());

        queries.list(null, null, null, pageable);

        ArgumentCaptor<Collection<UUID>> scopeCaptor = captor();
        ArgumentCaptor<Collection<EvaluationPanelStatus>> statusCaptor = captor();
        verify(panels).findInDepartments(eq(tenantId), eq(null), eq(null),
                statusCaptor.capture(), scopeCaptor.capture(), any());
        // Confined to EXACTLY the caller's assigned scope…
        assertThat(scopeCaptor.getValue()).containsExactly(deptA);
        // …and a non-status call passes the full status set (a no-op filter).
        assertThat(statusCaptor.getValue())
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(EvaluationPanelStatus.class));
        // The unfiltered tenant path is NEVER used for a scoped caller.
        verify(panels, never()).findAllByTenantId(any(), any());
    }

    @Test
    void committeeMemberStatusPullScopesAndKeepsRequestedStatuses() {
        setContext(Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), Set.of(deptA, deptB),
                Set.of("EVALUATION_READ"));
        when(panels.findInDepartments(eq(tenantId), any(), any(), any(), any(), any()))
                .thenReturn(emptyPage());

        queries.list(null, null, List.of(EvaluationPanelStatus.SUBMITTED), pageable);

        ArgumentCaptor<Collection<UUID>> scopeCaptor = captor();
        ArgumentCaptor<Collection<EvaluationPanelStatus>> statusCaptor = captor();
        verify(panels).findInDepartments(eq(tenantId), eq(null), eq(null),
                statusCaptor.capture(), scopeCaptor.capture(), any());
        assertThat(scopeCaptor.getValue()).containsExactlyInAnyOrder(deptA, deptB);
        assertThat(statusCaptor.getValue()).containsExactly(EvaluationPanelStatus.SUBMITTED);
        verify(panels, never()).findAllByTenantIdAndStatusIn(any(), any(), any());
    }

    @Test
    void departmentScopedProjectAndPositionProbeStillConfinedToScope() {
        // A scoped caller passing a foreign positionId cannot widen past their
        // subtree — the department IN-set still confines (and drives the count).
        setContext(Set.of(RoleCodes.DEPARTMENT_MANAGER), Set.of(deptA),
                Set.of("EVALUATION_READ"));
        when(panels.findInDepartments(eq(tenantId), any(), any(), any(), any(), any()))
                .thenReturn(emptyPage());

        queries.list(projectId, positionId, null, pageable);

        ArgumentCaptor<Collection<UUID>> scopeCaptor = captor();
        verify(panels).findInDepartments(eq(tenantId), eq(projectId), eq(positionId),
                any(), scopeCaptor.capture(), any());
        assertThat(scopeCaptor.getValue()).containsExactly(deptA);
        verify(panels, never()).findAllByTenantIdAndProjectIdAndPositionId(any(), any(), any(), any());
    }

    // --------------------------------------------- department-scoped: fail-closed

    @Test
    void departmentScopedWithEmptyScopeReturnsNoRowsAndTouchesNoFinder() {
        setContext(Set.of(RoleCodes.DEPARTMENT_MANAGER), Set.of(),
                Set.of("EVALUATION_READ"));

        Page<PanelResponse> page = queries.list(null, null, null, pageable);

        assertThat(page.getContent()).isEmpty();
        verify(panels, never()).findInDepartments(any(), any(), any(), any(), any(), any());
        verify(panels, never()).findAllByTenantId(any(), any());
        verify(panels, never()).findAllByTenantIdAndStatusIn(any(), any(), any());
    }

    @Test
    void departmentScopedWithNullScopeReturnsNoRowsAndTouchesNoFinder() {
        setContext(Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), null,
                Set.of("EVALUATION_READ"));

        Page<PanelResponse> page = queries.list(projectId, null,
                List.of(EvaluationPanelStatus.AVERAGED), pageable);

        assertThat(page.getContent()).isEmpty();
        verify(panels, never()).findInDepartments(any(), any(), any(), any(), any(), any());
        verify(panels, never()).findAllByTenantIdAndProjectId(any(), any(), any());
    }

    // --------------------------------------------------------------- helpers

    /** Empty source page — the routing (not the mapping) is under test. */
    private Page<EvaluationPanelJpaEntity> emptyPage() {
        return new PageImpl<>(List.of());
    }

    @SuppressWarnings("unchecked")
    private <T> ArgumentCaptor<T> captor() {
        return (ArgumentCaptor<T>) ArgumentCaptor.forClass(Collection.class);
    }

    private void setContext(Set<String> roles, Set<UUID> deptScope, Set<String> perms) {
        // Keep an unrelated position stub lenient in case a routing path ever loads
        // it; the empty source page short-circuits before mapping in these tests.
        lenient().when(positions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(new PositionJpaEntity(
                        positionId, tenantId, projectId, deptA, "P-001",
                        Map.of("ru-RU", "Кассир"), null, null, null, null, PositionStatus.ACTIVE)));
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(projectId),
                roles, perms, deptScope, false, "ru-RU"));
    }
}
