package uz.hrlab.grading.access.application;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.access.infrastructure.UserDepartmentScopeRepository;
import uz.hrlab.grading.access.infrastructure.UserProjectAssignmentRepository;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Offline unit tests for {@link UserScopeExpander} (E4-S0). Pure Mockito —
 * no Spring, no DB. Proves the four contract properties:
 * <ul>
 *   <li>CLAIM-FIRST: a non-empty JWT claim is honoured verbatim and the DB is
 *       never queried;</li>
 *   <li>DB FALLBACK: an empty claim triggers the DB lookup;</li>
 *   <li>SUBTREE CLOSURE: department scope roots are expanded to the subtree via
 *       the batched org-module CTE;</li>
 *   <li>FAIL-CLOSED: a DB error yields an EMPTY set, never a crash, never a
 *       widening.</li>
 * </ul>
 */
@Tag("unit")
class UserScopeExpanderTest {

    private final UserProjectAssignmentRepository projectAssignments =
            mock(UserProjectAssignmentRepository.class);
    private final UserDepartmentScopeRepository departmentScopes =
            mock(UserDepartmentScopeRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);

    private final UserScopeExpander expander =
            new UserScopeExpander(projectAssignments, departmentScopes, departments);

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    // ---------------------------------------------------------------- projects

    @Test
    void projectClaimFirstHonouredVerbatimAndDbNotQueried() {
        Set<UUID> claim = Set.of(UUID.randomUUID(), UUID.randomUUID());

        Set<UUID> result = expander.resolveProjectIds(userId, tenantId, claim);

        assertThat(result).isEqualTo(claim);
        verifyNoInteractions(projectAssignments);
    }

    @Test
    void projectEmptyClaimFallsBackToDb() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        when(projectAssignments.findActiveProjectIds(userId, tenantId))
                .thenReturn(List.of(p1, p2));

        Set<UUID> result = expander.resolveProjectIds(userId, tenantId, Set.of());

        assertThat(result).containsExactlyInAnyOrder(p1, p2);
        verify(projectAssignments).findActiveProjectIds(userId, tenantId);
    }

    @Test
    void projectFailClosedOnDbError() {
        when(projectAssignments.findActiveProjectIds(userId, tenantId))
                .thenThrow(new RuntimeException("db down"));

        Set<UUID> result = expander.resolveProjectIds(userId, tenantId, Set.of());

        // Fail-closed: empty, never crash, never widen.
        assertThat(result).isEmpty();
    }

    @Test
    void projectNoUserOrTenantYieldsEmptyWithoutQuery() {
        assertThat(expander.resolveProjectIds(null, tenantId, Set.of())).isEmpty();
        assertThat(expander.resolveProjectIds(userId, null, Set.of())).isEmpty();
        verifyNoInteractions(projectAssignments);
    }

    // ------------------------------------------------------------- departments

    @Test
    void departmentClaimFirstHonouredVerbatimAndDbNotQueried() {
        Set<UUID> claim = Set.of(UUID.randomUUID());

        Set<UUID> result = expander.resolveDepartmentScope(userId, tenantId, claim);

        assertThat(result).isEqualTo(claim);
        verifyNoInteractions(departmentScopes, departments);
    }

    @Test
    void departmentEmptyClaimExpandsRootsToSubtree() {
        UUID root = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        UUID grandchild = UUID.randomUUID();
        when(departmentScopes.findActiveDepartmentIds(userId, tenantId))
                .thenReturn(List.of(root));
        // The batched CTE returns root + all descendants.
        when(departments.findSubtreeIds(anyCollection(), eq(tenantId)))
                .thenReturn(List.of(root, child, grandchild));

        Set<UUID> result = expander.resolveDepartmentScope(userId, tenantId, Set.of());

        assertThat(result).containsExactlyInAnyOrder(root, child, grandchild);
        verify(departments).findSubtreeIds(anyCollection(), eq(tenantId));
    }

    @Test
    void departmentNoScopeRowsYieldsEmptyAndDoesNotCallSubtree() {
        when(departmentScopes.findActiveDepartmentIds(userId, tenantId))
                .thenReturn(List.of());

        Set<UUID> result = expander.resolveDepartmentScope(userId, tenantId, Set.of());

        assertThat(result).isEmpty();
        verify(departments, never()).findSubtreeIds(any(), any());
    }

    @Test
    void departmentFallsBackToRootsWhenSubtreeEmpty() {
        UUID root = UUID.randomUUID();
        when(departmentScopes.findActiveDepartmentIds(userId, tenantId))
                .thenReturn(List.of(root));
        when(departments.findSubtreeIds(anyCollection(), eq(tenantId)))
                .thenReturn(List.of());

        Set<UUID> result = expander.resolveDepartmentScope(userId, tenantId, Set.of());

        // Defense in depth — at least the granted root is honoured.
        assertThat(result).containsExactly(root);
    }

    @Test
    void departmentFailClosedOnDbError() {
        when(departmentScopes.findActiveDepartmentIds(userId, tenantId))
                .thenThrow(new RuntimeException("db down"));

        Set<UUID> result = expander.resolveDepartmentScope(userId, tenantId, Set.of());

        assertThat(result).isEmpty();
    }

    @Test
    void departmentFailClosedWhenSubtreeQueryThrows() {
        when(departmentScopes.findActiveDepartmentIds(userId, tenantId))
                .thenReturn(List.of(UUID.randomUUID()));
        when(departments.findSubtreeIds(anyCollection(), eq(tenantId)))
                .thenThrow(new RuntimeException("cte error"));

        Set<UUID> result = expander.resolveDepartmentScope(userId, tenantId, Set.of());

        assertThat(result).isEmpty();
    }
}
