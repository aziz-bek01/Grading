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

    private TenantContext ctxWithRoles(Set<String> roles, Set<UUID> deptScope) {
        return new TenantContext(
                UUID.randomUUID(), UUID.randomUUID(), Set.of(), roles, Set.of(),
                deptScope, false, "ru-RU");
    }
}
