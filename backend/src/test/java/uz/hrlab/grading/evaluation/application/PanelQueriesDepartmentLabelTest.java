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
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.organization.domain.DepartmentStatus;
import uz.hrlab.grading.organization.domain.DepartmentType;
import uz.hrlab.grading.organization.infrastructure.DepartmentHierarchyResolver;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CEO inline sign-off — Task B. {@code PanelQueries.list} adds two localized
 * labels per row for the CEO table:
 * <ul>
 *   <li>{@code department_label_i18n} (Departament) — the position's TOP-LEVEL
 *       ancestor department name;</li>
 *   <li>{@code division_label_i18n} (Bo'limi) — the position's OWN leaf
 *       department name when nested under that ancestor, else null/omitted.</li>
 * </ul>
 * Split logic reuses the evaluation-report ancestor-walk pattern. These tests
 * prove the ancestor/leaf split, the top-level-position null-division case,
 * tenant scoping, and that departments are batch-loaded (no per-panel N+1).
 */
@ExtendWith(MockitoExtension.class)
class PanelQueriesDepartmentLabelTest {

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
    Pageable pageable = PageRequest.of(0, 50);

    // Department tree: ROOT (top-level) -> CHILD (division) -> GRANDCHILD.
    UUID rootDeptId;
    UUID childDeptId;
    UUID grandchildDeptId;

    @BeforeEach
    void setUp() {
        // Real (stateless) filter: CLIENT_HR_DIRECTOR is a tenant-wide bypass, so it
        // returns empty (unfiltered) — these label tests keep the unfiltered path.
        queries = new PanelQueries(panels, assignments, averages, evaluations, scores,
                positions, factors, actorNames, abacGate, departments, departmentScopes,
                new DepartmentScopeFilter(), new DepartmentHierarchyResolver(departments));
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        rootDeptId = UUID.randomUUID();
        childDeptId = UUID.randomUUID();
        grandchildDeptId = UUID.randomUUID();
        setResultViewerContext();
        // Roster + averages are irrelevant to the label assertions — keep them empty.
        lenient().when(assignments.findAllByTenantIdAndPanelIdIn(eq(tenantId), any()))
                .thenReturn(List.of());
        lenient().when(averages.findEvaluatorCountsByPanelIds(eq(tenantId), any()))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() { TenantContextHolder.clear(); }

    @Test
    void nestedPositionExposesBothAncestorDepartmentAndOwnDivision() {
        // Position sits on the GRANDCHILD department → Departament = ROOT,
        // Bo'limi = the position's OWN leaf (GRANDCHILD).
        UUID positionId = UUID.randomUUID();
        UUID panelId = UUID.randomUUID();
        EvaluationPanelJpaEntity panel = panel(panelId, positionId, EvaluationPanelStatus.SUBMITTED);
        when(panels.findAllByTenantIdAndProjectId(eq(tenantId), eq(projectId), any()))
                .thenReturn(new PageImpl<>(List.of(panel)));
        when(positions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(position(positionId, grandchildDeptId)));
        stubFullClosure();

        Page<PanelResponse> page = queries.list(projectId, null, null, pageable);

        PanelResponse row = page.getContent().get(0);
        assertThat(row.departmentLabelI18n()).containsEntry("ru-RU", "Корневой департамент");
        assertThat(row.divisionLabelI18n()).containsEntry("ru-RU", "Внучатый отдел");
    }

    @Test
    void directChildPositionExposesRootAsDepartmentAndChildAsDivision() {
        UUID positionId = UUID.randomUUID();
        UUID panelId = UUID.randomUUID();
        EvaluationPanelJpaEntity panel = panel(panelId, positionId, EvaluationPanelStatus.AVERAGED);
        when(panels.findAllByTenantIdAndProjectId(eq(tenantId), eq(projectId), any()))
                .thenReturn(new PageImpl<>(List.of(panel)));
        when(positions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(position(positionId, childDeptId)));
        stubFullClosure();

        PanelResponse row = queries.list(projectId, null, null, pageable).getContent().get(0);

        assertThat(row.departmentLabelI18n()).containsEntry("ru-RU", "Корневой департамент");
        assertThat(row.divisionLabelI18n()).containsEntry("ru-RU", "Дочерний отдел");
    }

    @Test
    void topLevelPositionHasDepartmentButNullDivision() {
        // Position sits directly on the ROOT (no parent) → Departament = ROOT,
        // Bo'limi = null (NON_NULL omits it from the wire).
        UUID positionId = UUID.randomUUID();
        UUID panelId = UUID.randomUUID();
        EvaluationPanelJpaEntity panel = panel(panelId, positionId, EvaluationPanelStatus.SUBMITTED);
        when(panels.findAllByTenantIdAndProjectId(eq(tenantId), eq(projectId), any()))
                .thenReturn(new PageImpl<>(List.of(panel)));
        when(positions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(position(positionId, rootDeptId)));
        stubFullClosure();

        PanelResponse row = queries.list(projectId, null, null, pageable).getContent().get(0);

        assertThat(row.departmentLabelI18n()).containsEntry("ru-RU", "Корневой департамент");
        assertThat(row.divisionLabelI18n()).isNull();
    }

    @Test
    void positionWithoutDepartmentHasNoLabels() {
        UUID positionId = UUID.randomUUID();
        UUID panelId = UUID.randomUUID();
        EvaluationPanelJpaEntity panel = panel(panelId, positionId, EvaluationPanelStatus.SUBMITTED);
        when(panels.findAllByTenantIdAndProjectId(eq(tenantId), eq(projectId), any()))
                .thenReturn(new PageImpl<>(List.of(panel)));
        when(positions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(position(positionId, null)));
        // No department ids collected → no closure load at all.

        PanelResponse row = queries.list(projectId, null, null, pageable).getContent().get(0);

        assertThat(row.departmentLabelI18n()).isNull();
        assertThat(row.divisionLabelI18n()).isNull();
        // No leaf department → the closure loader is never queried.
        verify(departments, times(0)).findAllByTenantIdAndIdIn(any(), any());
    }

    @Test
    void departmentsAreBatchLoadedTenantScopedWithoutPerPanelN1() {
        // THREE panels on THREE distinct leaf departments across the SAME tree.
        // The closure is loaded by batched findAllByTenantIdAndIdIn — NOT one
        // findByIdAndTenantId per panel. Tenant is pinned in every closure query.
        UUID posA = UUID.randomUUID();
        UUID posB = UUID.randomUUID();
        UUID posC = UUID.randomUUID();
        EvaluationPanelJpaEntity pa = panel(UUID.randomUUID(), posA, EvaluationPanelStatus.SUBMITTED);
        EvaluationPanelJpaEntity pb = panel(UUID.randomUUID(), posB, EvaluationPanelStatus.SUBMITTED);
        EvaluationPanelJpaEntity pc = panel(UUID.randomUUID(), posC, EvaluationPanelStatus.SUBMITTED);
        when(panels.findAllByTenantIdAndProjectId(eq(tenantId), eq(projectId), any()))
                .thenReturn(new PageImpl<>(List.of(pa, pb, pc)));
        when(positions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(
                        position(posA, grandchildDeptId),
                        position(posB, childDeptId),
                        position(posC, rootDeptId)));
        stubFullClosure();

        Page<PanelResponse> page = queries.list(projectId, null, null, pageable);
        assertThat(page.getContent()).hasSize(3);

        // Closure loads are batched + tenant-scoped (each call pins the active
        // tenant; never the per-row findByIdAndTenantId leakage path).
        ArgumentCaptor<UUID> tenantCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<Collection<UUID>> idsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(departments, atLeastOnce())
                .findAllByTenantIdAndIdIn(tenantCaptor.capture(), idsCaptor.capture());
        assertThat(tenantCaptor.getAllValues()).containsOnly(tenantId);
        // The FIRST closure level requests all THREE leaf ids in ONE batched call.
        assertThat(idsCaptor.getAllValues().get(0))
                .containsExactlyInAnyOrder(grandchildDeptId, childDeptId, rootDeptId);
    }

    // --------------------------------------------------------------- helpers

    private EvaluationPanelJpaEntity panel(UUID panelId, UUID positionId, EvaluationPanelStatus status) {
        return new EvaluationPanelJpaEntity(
                panelId, tenantId, projectId, positionId, UUID.randomUUID(), status, 3);
    }

    private PositionJpaEntity position(UUID positionId, UUID departmentId) {
        return new PositionJpaEntity(
                positionId, tenantId, projectId, departmentId, "P-001",
                Map.of("ru-RU", "Кассир"), null, null, null, null, PositionStatus.ACTIVE);
    }

    private DepartmentJpaEntity dept(UUID id, UUID parentId, String code, String ruName) {
        return new DepartmentJpaEntity(
                id, tenantId, projectId, parentId, code, Map.of("ru-RU", ruName),
                DepartmentType.DEPARTMENT, DepartmentStatus.ACTIVE);
    }

    /**
     * Stub the batched closure load level by level (matches the use case's
     * frontier-by-frontier walk): leaf ids resolve to their entities, whose
     * parents are requested in the next call. The matcher keys off the requested
     * id set so each level returns exactly the entities for that frontier.
     */
    private void stubFullClosure() {
        DepartmentJpaEntity root = dept(rootDeptId, null, "ROOT", "Корневой департамент");
        DepartmentJpaEntity child = dept(childDeptId, rootDeptId, "CHILD", "Дочерний отдел");
        DepartmentJpaEntity grandchild =
                dept(grandchildDeptId, childDeptId, "GC", "Внучатый отдел");
        lenient().when(departments.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenAnswer(inv -> {
                    Collection<UUID> ids = inv.getArgument(1);
                    return List.of(root, child, grandchild).stream()
                            .filter(d -> ids.contains(d.getId()))
                            .toList();
                });
    }

    private void setResultViewerContext() {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(projectId),
                Set.of(RoleCodes.CLIENT_HR_DIRECTOR),
                Set.of("EVALUATION_READ", "CAMPAIGN_RESULTS_VIEW"), Set.of(), false, "ru-RU"));
    }
}
