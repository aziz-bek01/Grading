package uz.hrlab.grading.access.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.access.application.AbacRequest;
import uz.hrlab.grading.access.application.PolicyDecision;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("abac")
class DepartmentScopePolicyTest {

    private final DepartmentScopePolicy policy = new DepartmentScopePolicy();

    @Test
    void notApplicableWhenNoDepartmentInRequest() {
        TenantContext ctx = ctxWithRoles(Set.of(RoleCodes.DEPARTMENT_MANAGER), Set.of());
        AbacRequest req = new AbacRequest(ctx, "Project", null, UUID.randomUUID(), null, null);
        assertThat(policy.evaluate(req)).isEqualTo(PolicyDecision.NOT_APPLICABLE);
    }

    @Test
    void permitsHrlabRolesBypass() {
        UUID dept = UUID.randomUUID();
        TenantContext ctx = ctxWithRoles(Set.of(RoleCodes.HRLAB_PROJECT_MANAGER), Set.of());
        AbacRequest req = new AbacRequest(ctx, "Department", dept, null, dept, null);
        assertThat(policy.evaluate(req)).isEqualTo(PolicyDecision.PERMIT);
    }

    @Test
    void deniesDepartmentManagerOutsideScope() {
        UUID inScope = UUID.randomUUID();
        UUID outOfScope = UUID.randomUUID();
        TenantContext ctx = ctxWithRoles(Set.of(RoleCodes.DEPARTMENT_MANAGER), Set.of(inScope));
        AbacRequest req = new AbacRequest(ctx, "Department", outOfScope, null, outOfScope, null);
        assertThat(policy.evaluate(req)).isEqualTo(PolicyDecision.DENY);
    }

    @Test
    void permitsDepartmentManagerInsideScope() {
        UUID dept = UUID.randomUUID();
        TenantContext ctx = ctxWithRoles(Set.of(RoleCodes.DEPARTMENT_MANAGER), Set.of(dept));
        AbacRequest req = new AbacRequest(ctx, "Department", dept, null, dept, null);
        assertThat(policy.evaluate(req)).isEqualTo(PolicyDecision.PERMIT);
    }

    // ---- E4-S2/S3: EVALUATION_COMMITTEE_MEMBER (the "evaluation expert") is
    //      now department-scoped too (same treatment as DEPARTMENT_MANAGER). ----

    @Test
    void deniesEvaluationCommitteeMemberOutsideScope() {
        UUID inScope = UUID.randomUUID();
        UUID outOfScope = UUID.randomUUID();
        TenantContext ctx = ctxWithRoles(
                Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), Set.of(inScope));
        AbacRequest req = new AbacRequest(ctx, "Position", outOfScope, null, outOfScope, null);
        assertThat(policy.evaluate(req)).isEqualTo(PolicyDecision.DENY);
    }

    @Test
    void permitsEvaluationCommitteeMemberInsideScope() {
        UUID dept = UUID.randomUUID();
        TenantContext ctx = ctxWithRoles(
                Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), Set.of(dept));
        AbacRequest req = new AbacRequest(ctx, "Position", dept, null, dept, null);
        assertThat(policy.evaluate(req)).isEqualTo(PolicyDecision.PERMIT);
    }

    @Test
    void deniesDepartmentScopedRoleWithEmptyScopeFailClosed() {
        UUID anyDept = UUID.randomUUID();
        TenantContext ctx = ctxWithRoles(
                Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), Set.of());
        AbacRequest req = new AbacRequest(ctx, "Position", anyDept, null, anyDept, null);
        assertThat(policy.evaluate(req))
                .as("empty scope + department-scoped role must DENY (fail-closed)")
                .isEqualTo(PolicyDecision.DENY);
    }

    @Test
    void bypassRoleIsNotDepartmentScoped() {
        TenantContext ctx = ctxWithRoles(Set.of(RoleCodes.CLIENT_HR_DIRECTOR), Set.of());
        assertThat(DepartmentScopePolicy.isDepartmentScoped(ctx)).isFalse();
        assertThat(DepartmentScopePolicy.isTenantWideBypass(ctx)).isTrue();
    }

    @Test
    void clientCeoIsTenantWideBypassNotDepartmentScoped() {
        // REQ-CEO — the CEO reads panels / positions / results across EVERY
        // department (same treatment as the HR Director); they are never confined
        // to a department subtree.
        TenantContext ctx = ctxWithRoles(Set.of(RoleCodes.CLIENT_CEO), Set.of());
        assertThat(DepartmentScopePolicy.isTenantWideBypass(ctx)).isTrue();
        assertThat(DepartmentScopePolicy.isDepartmentScoped(ctx)).isFalse();

        UUID dept = UUID.randomUUID();
        AbacRequest req = new AbacRequest(ctx, "Position", dept, null, dept, null);
        assertThat(policy.evaluate(req)).isEqualTo(PolicyDecision.PERMIT);
    }

    @Test
    void departmentScopedRolesAreClassifiedScoped() {
        assertThat(DepartmentScopePolicy.isDepartmentScoped(
                ctxWithRoles(Set.of(RoleCodes.DEPARTMENT_MANAGER), Set.of()))).isTrue();
        assertThat(DepartmentScopePolicy.isDepartmentScoped(
                ctxWithRoles(Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), Set.of()))).isTrue();
    }

    @Test
    void unrelatedRoleIsNotDepartmentScoped() {
        // A role that is neither a bypass nor one of the two constrained roles
        // (e.g. CLIENT_HR_SPECIALIST) is not department-confined by this policy.
        assertThat(DepartmentScopePolicy.isDepartmentScoped(
                ctxWithRoles(Set.of(RoleCodes.CLIENT_HR_SPECIALIST), Set.of()))).isFalse();
    }

    private TenantContext ctxWithRoles(Set<String> roles, Set<UUID> deptScope) {
        return new TenantContext(
                UUID.randomUUID(), UUID.randomUUID(), Set.of(), roles, Set.of(),
                deptScope, false, "ru-RU");
    }
}
