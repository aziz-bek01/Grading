package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.domain.DepartmentScopePolicy;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * E4-S2 — central decision for whether a LIST read must be confined to the
 * caller's assigned department subtree, and to WHICH department ids.
 *
 * <p>This is the read-side companion to {@link DepartmentScopePolicy} (which
 * gates per-row reads/writes). It exists so the list queries
 * ({@code FindPositionQuery}, {@code EvaluationQueries}) never re-list role
 * codes: the role classification lives in exactly one place
 * ({@link DepartmentScopePolicy#isDepartmentScoped(TenantContext)} /
 * {@link DepartmentScopePolicy#isTenantWideBypass(TenantContext)}).
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>{@link Optional#empty()} — the caller is NOT department-scoped (tenant-
 *       wide bypass role, or any role outside the two constrained roles). The
 *       query must run UNFILTERED (current behaviour preserved).</li>
 *   <li>A PRESENT set — the caller is department-scoped. The query must add a
 *       {@code department_id IN (:scope)} predicate. The set is exactly the
 *       caller's {@code TenantContext.departmentScope()} (subtree-closed and
 *       populated by {@code UserScopeExpander}).</li>
 *   <li>A present but EMPTY set — fail-closed: the caller is department-scoped
 *       but has been assigned NO department, so they must see ZERO rows. The
 *       repository finders translate an empty IN-set into a no-match (never a
 *       no-op that would widen access).</li>
 * </ul>
 */
@Component
public class DepartmentScopeFilter {

    /**
     * Resolve the department-id allow-list for a LIST read by {@code ctx}.
     *
     * @return {@link Optional#empty()} when the query must NOT be filtered
     *         (bypass / non-scoped role); otherwise the present (possibly empty,
     *         meaning fail-closed) set of allowed department ids.
     */
    public Optional<Set<UUID>> allowedDepartmentIds(TenantContext ctx) {
        if (!DepartmentScopePolicy.isDepartmentScoped(ctx)) {
            return Optional.empty(); // unfiltered — bypass or non-constrained role
        }
        Set<UUID> scope = ctx.departmentScope();
        // Department-scoped caller: a null scope is treated as the empty set so
        // an unscoped expert/manager sees nothing (fail-closed), never all.
        return Optional.of(scope == null ? Set.of() : scope);
    }
}
