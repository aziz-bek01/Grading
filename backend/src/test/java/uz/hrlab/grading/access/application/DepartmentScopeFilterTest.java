package uz.hrlab.grading.access.application;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E4-S2 — unit test for {@link DepartmentScopeFilter}: the single decision point
 * that drives the {@code department_id IN (:scope)} predicate on list reads.
 *
 * <ul>
 *   <li>bypass role → {@link Optional#empty()} (unfiltered, sees all);</li>
 *   <li>department-scoped role with a non-empty scope → present subset;</li>
 *   <li>department-scoped role with an EMPTY / null scope → present-but-empty
 *       (fail-closed: zero rows).</li>
 * </ul>
 */
@Tag("abac")
class DepartmentScopeFilterTest {

    private final DepartmentScopeFilter filter = new DepartmentScopeFilter();

    @Test
    void bypassRoleIsUnfiltered() {
        TenantContext ctx = ctx(Set.of(RoleCodes.CLIENT_HR_DIRECTOR), Set.of());
        assertThat(filter.allowedDepartmentIds(ctx))
                .as("tenant-wide bypass → no department filter")
                .isEmpty();
    }

    @Test
    void superAdminIsUnfiltered() {
        TenantContext ctx = ctx(Set.of(RoleCodes.HRLAB_SUPER_ADMIN),
                Set.of(UUID.randomUUID()));
        // Even if a scope set happens to be present, a bypass role is unfiltered.
        assertThat(filter.allowedDepartmentIds(ctx)).isEmpty();
    }

    @Test
    void nonScopedNonBypassRoleIsUnfiltered() {
        TenantContext ctx = ctx(Set.of(RoleCodes.CLIENT_HR_SPECIALIST), Set.of());
        assertThat(filter.allowedDepartmentIds(ctx)).isEmpty();
    }

    @Test
    void departmentManagerWithScopeReturnsSubset() {
        UUID d1 = UUID.randomUUID();
        UUID d2 = UUID.randomUUID();
        TenantContext ctx = ctx(Set.of(RoleCodes.DEPARTMENT_MANAGER), Set.of(d1, d2));
        Optional<Set<UUID>> allowed = filter.allowedDepartmentIds(ctx);
        assertThat(allowed).isPresent();
        assertThat(allowed.get()).containsExactlyInAnyOrder(d1, d2);
    }

    @Test
    void evaluationExpertWithScopeReturnsSubset() {
        UUID d1 = UUID.randomUUID();
        TenantContext ctx = ctx(Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), Set.of(d1));
        Optional<Set<UUID>> allowed = filter.allowedDepartmentIds(ctx);
        assertThat(allowed).isPresent();
        assertThat(allowed.get()).containsExactly(d1);
    }

    @Test
    void departmentScopedRoleWithEmptyScopeIsFailClosed() {
        TenantContext ctx = ctx(Set.of(RoleCodes.DEPARTMENT_MANAGER), Set.of());
        Optional<Set<UUID>> allowed = filter.allowedDepartmentIds(ctx);
        assertThat(allowed)
                .as("present-but-empty → caller is scoped but assigned nothing → zero rows")
                .isPresent();
        assertThat(allowed.get()).isEmpty();
    }

    @Test
    void departmentScopedRoleWithNullScopeIsFailClosed() {
        TenantContext ctx = ctx(Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), null);
        Optional<Set<UUID>> allowed = filter.allowedDepartmentIds(ctx);
        assertThat(allowed).isPresent();
        assertThat(allowed.get()).isEmpty();
    }

    private TenantContext ctx(Set<String> roles, Set<UUID> deptScope) {
        return new TenantContext(
                UUID.randomUUID(), UUID.randomUUID(), Set.of(), roles, Set.of(),
                deptScope, false, "ru-RU");
    }
}
