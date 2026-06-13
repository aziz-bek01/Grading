package uz.hrlab.grading.organization.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.organization.domain.DepartmentStatus;
import uz.hrlab.grading.organization.domain.DepartmentType;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Defect-1 BE — {@link FindDepartmentQuery#positionCounts}. Proves the server-
 * authoritative roll-up:
 * <ul>
 *   <li>a parent with its OWN positions AND children returns directCount = its own
 *       count and subtreeCount = self + every descendant (fixes 1a — parents now
 *       aggregate descendants);</li>
 *   <li>counts come from the ONE grouped query and are NOT a capped page, so a
 *       department with > 200 positions reports the true count (fixes 1b);</li>
 *   <li>a department-scoped caller only sees counts for in-scope departments
 *       (reuses the same {@code filterByDepartmentScope} as the tree read);</li>
 *   <li>no N+1: exactly ONE grouped count query and ZERO {@code findSubtreeIds}
 *       calls (the roll-up is in-memory over the already-loaded flat list).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FindDepartmentQueryCountsTest {

    @Mock DepartmentRepository departments;
    @Mock PositionRepository positions;
    @Mock AbacGate abacGate;

    FindDepartmentQuery query;

    UUID tenantId;
    UUID projectId;
    UUID root;     // parent with its own positions + children
    UUID childA;   // leaf with positions
    UUID childB;   // leaf with a very large position count

    @BeforeEach
    void setUp() {
        query = new FindDepartmentQuery(departments, positions, abacGate);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        root = UUID.randomUUID();
        childA = UUID.randomUUID();
        childB = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void parentRollsUpDescendantsAndIsNotTruncatedByPageCap() {
        setContext(Set.of("CLIENT_HR_DIRECTOR"), Set.of()); // bypass — sees all
        stubTree();
        // childB has 5000 positions — far beyond any 200-row page cap, proving the
        // grouped query (not a capped position page) backs the counts (defect 1b).
        when(positions.countActiveByDepartment(tenantId, projectId)).thenReturn(List.of(
                count(root, 3),
                count(childA, 10),
                count(childB, 5000)));

        List<DepartmentPositionCount> result = query.positionCounts(projectId);

        DepartmentPositionCount rootCount = find(result, root);
        // 1a: parent's directCount is its OWN positions only...
        assertThat(rootCount.directCount()).isEqualTo(3);
        // ...but subtreeCount rolls up self + childA + childB.
        assertThat(rootCount.subtreeCount()).isEqualTo(3 + 10 + 5000);

        assertThat(find(result, childA).directCount()).isEqualTo(10);
        assertThat(find(result, childA).subtreeCount()).isEqualTo(10);
        assertThat(find(result, childB).directCount()).isEqualTo(5000);
        assertThat(find(result, childB).subtreeCount()).isEqualTo(5000);

        // No N+1: one grouped query, zero closure calls.
        verify(positions, times(1)).countActiveByDepartment(tenantId, projectId);
        verify(departments, never()).findSubtreeIds(any(), any());
    }

    @Test
    void departmentAbsentFromGroupedResultDefaultsToZero() {
        setContext(Set.of("CLIENT_HR_DIRECTOR"), Set.of());
        stubTree();
        // Only childA reported — root and childB have no active positions.
        when(positions.countActiveByDepartment(tenantId, projectId))
                .thenReturn(List.of(count(childA, 4)));

        List<DepartmentPositionCount> result = query.positionCounts(projectId);

        assertThat(find(result, root).directCount()).isZero();
        assertThat(find(result, root).subtreeCount()).isEqualTo(4); // rolls up childA
        assertThat(find(result, childB).directCount()).isZero();
        assertThat(find(result, childB).subtreeCount()).isZero();
    }

    @Test
    void departmentScopedCallerSeesOnlyInScopeDepartments() {
        // Department-scoped (non-bypass) role with scope {childA} only.
        setContext(Set.of("DEPARTMENT_MANAGER"), Set.of(childA));
        stubTree();
        when(positions.countActiveByDepartment(tenantId, projectId)).thenReturn(List.of(
                count(root, 3),
                count(childA, 10),
                count(childB, 7)));

        List<DepartmentPositionCount> result = query.positionCounts(projectId);

        // Reuses filterByDepartmentScope → only childA is visible.
        assertThat(result).hasSize(1);
        assertThat(result.get(0).departmentId()).isEqualTo(childA);
        assertThat(result.get(0).directCount()).isEqualTo(10);
        assertThat(result.get(0).subtreeCount()).isEqualTo(10);
        verify(departments, never()).findSubtreeIds(any(), any());
    }

    // ---------- helpers ----------

    /** root → {childA, childB}. */
    private void stubTree() {
        when(departments.findByTenantIdAndProjectId(tenantId, projectId)).thenReturn(List.of(
                dept(root, null),
                dept(childA, root),
                dept(childB, root)));
    }

    private DepartmentJpaEntity dept(UUID id, UUID parentId) {
        return new DepartmentJpaEntity(id, tenantId, projectId, parentId, "D-" + id,
                Map.of("ru-RU", "Dept"), DepartmentType.DEPARTMENT, DepartmentStatus.ACTIVE);
    }

    private PositionRepository.DeptCountProjection count(UUID deptId, long n) {
        return new PositionRepository.DeptCountProjection() {
            @Override public UUID getDepartmentId() { return deptId; }
            @Override public long getCount() { return n; }
        };
    }

    private DepartmentPositionCount find(List<DepartmentPositionCount> rows, UUID deptId) {
        return rows.stream().filter(r -> r.departmentId().equals(deptId)).findFirst().orElseThrow();
    }

    private void setContext(Set<String> roles, Set<UUID> deptScope) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(projectId), roles,
                Set.of("ORG_READ"), deptScope, false, "ru-RU"));
    }
}
