package uz.hrlab.grading.access.application;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.access.domain.DepartmentScopePolicy;
import uz.hrlab.grading.access.domain.ProjectMembershipPolicy;
import uz.hrlab.grading.access.infrastructure.UserDepartmentScopeRepository;
import uz.hrlab.grading.access.infrastructure.UserProjectAssignmentRepository;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * E4-S0 acceptance: an UNSCOPED user (no rows in user_department_scopes /
 * user_project_assignments — i.e. EVERYONE today) is UNAFFECTED by the new
 * scope-population wiring. This is the "no new denials" guarantee — enforcement
 * semantics are NOT changed in this slice.
 *
 * <p>It chains the real {@link UserScopeExpander} (DB returns nothing) into the
 * EXISTING, UNCHANGED {@link DepartmentScopePolicy} / {@link ProjectMembershipPolicy}
 * to prove their empty-scope behaviour is identical to before:
 * <ul>
 *   <li>{@code DepartmentScopePolicy}: an empty scope is treated as "no
 *       manager-scope" — only the {@code DEPARTMENT_MANAGER} role is constrained,
 *       and even then only when a non-empty set excludes the target. With an
 *       empty set + a non-DEPARTMENT_MANAGER role → NOT_APPLICABLE (no deny).</li>
 *   <li>{@code ProjectMembershipPolicy}: a tenant-wide-bypass role with empty
 *       projectIds is PERMITTED (no per-project membership required).</li>
 * </ul>
 */
@Tag("security")
class UnscopedUserUnaffectedTest {

    private final UserProjectAssignmentRepository projectAssignments =
            mock(UserProjectAssignmentRepository.class);
    private final UserDepartmentScopeRepository departmentScopes =
            mock(UserDepartmentScopeRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);

    private final UserScopeExpander expander =
            new UserScopeExpander(projectAssignments, departmentScopes, departments);

    private final DepartmentScopePolicy deptPolicy = new DepartmentScopePolicy();
    private final ProjectMembershipPolicy projectPolicy = new ProjectMembershipPolicy();

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @Test
    void unscopedUserGetsEmptyScopeFromExpander() {
        when(projectAssignments.findActiveProjectIds(userId, tenantId)).thenReturn(List.of());
        when(departmentScopes.findActiveDepartmentIds(userId, tenantId)).thenReturn(List.of());

        Set<UUID> projects = expander.resolveProjectIds(userId, tenantId, Set.of());
        Set<UUID> depts = expander.resolveDepartmentScope(userId, tenantId, Set.of());

        assertThat(projects).isEmpty();
        assertThat(depts).isEmpty();
    }

    @Test
    void emptyDepartmentScopeDoesNotDenyNonDepartmentManager() {
        // A regular consultant with an empty department scope (today's default).
        TenantContext ctx = ctx(Set.of(RoleCodes.HRLAB_CONSULTANT), Set.of(), Set.of());
        UUID someDept = UUID.randomUUID();
        AbacRequest req = new AbacRequest(ctx, "Department", someDept, null, someDept, null);

        // Bypass role → PERMIT, exactly as today. No new denial.
        assertThat(deptPolicy.evaluate(req))
                .isEqualTo(uz.hrlab.grading.access.application.PolicyDecision.PERMIT);
    }

    @Test
    void emptyProjectScopeForBypassRolePermits() {
        // CLIENT_HR_DIRECTOR is a tenant-wide bypass role; empty projectIds is fine.
        TenantContext ctx = ctx(Set.of(RoleCodes.CLIENT_HR_DIRECTOR), Set.of(), Set.of());
        UUID someProject = UUID.randomUUID();
        AbacRequest req = new AbacRequest(ctx, "Project", someProject, someProject, null, null);

        assertThat(projectPolicy.evaluate(req))
                .isEqualTo(uz.hrlab.grading.access.application.PolicyDecision.PERMIT);
    }

    /**
     * P3-1 (no-regression): a NON-bypass project-scoped role (HRLAB_CONSULTANT)
     * with an EMPTY projectIds set was already DENIED for a specific project
     * before this slice, and MUST remain DENIED after it. This slice only adds
     * the MECHANISM (project-assignment rows) to un-break such a user via an
     * explicit grant — it does NOT change the empty-scope decision, so no
     * already-deployed consultant silently gains or loses access on rollout.
     */
    @Test
    void emptyProjectScopeForNonBypassRoleStillDeniedNoRegression() {
        TenantContext ctx = ctx(Set.of(RoleCodes.HRLAB_CONSULTANT), Set.of(), Set.of());
        UUID someProject = UUID.randomUUID();
        AbacRequest req = new AbacRequest(ctx, "Project", someProject, someProject, null, null);

        // Same decision the policy yielded before the slice: fail-closed DENY.
        assertThat(projectPolicy.evaluate(req))
                .isEqualTo(uz.hrlab.grading.access.application.PolicyDecision.DENY);

        // And an EXPLICIT assignment (the slice's mechanism) flips it to PERMIT —
        // proving the un-break path works without changing the empty default.
        TenantContext assigned = ctx(Set.of(RoleCodes.HRLAB_CONSULTANT),
                Set.of(someProject), Set.of());
        AbacRequest assignedReq =
                new AbacRequest(assigned, "Project", someProject, someProject, null, null);
        assertThat(projectPolicy.evaluate(assignedReq))
                .isEqualTo(uz.hrlab.grading.access.application.PolicyDecision.PERMIT);
    }

    private TenantContext ctx(Set<String> roles, Set<UUID> projectIds, Set<UUID> deptScope) {
        return new TenantContext(userId, tenantId, projectIds, roles, Set.of(),
                deptScope, false, "ru-RU");
    }
}
