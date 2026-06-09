package uz.hrlab.grading.access.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.infrastructure.UserDepartmentScopeRepository;
import uz.hrlab.grading.access.infrastructure.UserProjectAssignmentRepository;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Single source of truth for expanding a user's ABAC SCOPE — project ids and
 * department subtree — for one active (user, tenant) pair (E4-S0).
 *
 * <p>Reused by ALL three context-builder paths so they resolve identical scope
 * for identical DB state:
 * <ul>
 *   <li>{@code JwtTenantContextResolver} (production OIDC path);</li>
 *   <li>{@code DevUserAuthorityResolver} / {@code DevAuthFilter} (dev path);</li>
 *   <li>{@code TenantContextFilter.withMemberships} (membership preload).</li>
 * </ul>
 *
 * <h3>Claim-first (mirrors RBAC {@code expandAuthority})</h3>
 * If the JWT already carried a non-empty scope claim, the IdP is authoritative
 * and that claim is honoured verbatim — callers pass the claim set and this
 * expander only falls back to the DB when the claim is absent/empty. The same
 * forward-compatible contract as roles/permissions.
 *
 * <h3>Department SUBTREE</h3>
 * A scope row on department {@code D} grants visibility of {@code D} AND all of
 * its descendants. The closure is computed by a SINGLE batched recursive CTE
 * ({@code DepartmentRepository.findSubtreeIds}) — the existing org-module tree
 * logic, NOT a reimplementation, and no per-root N+1.
 *
 * <h3>Fail-closed + safe</h3>
 * Any error (DB hiccup, missing tenant) is logged and yields an EMPTY scope set
 * — never a crash of the auth path, never a WIDENING of access. An empty
 * department scope means "no explicit manager-scope", which the EXISTING
 * {@code DepartmentScopePolicy} treats exactly as it does today (only the
 * {@code DEPARTMENT_MANAGER} role is constrained by a non-empty set). So an
 * unscoped user is unaffected.
 *
 * <h3>Performance</h3>
 * On the request/auth path on a small VPS: at most ONE query for project rows,
 * ONE for department scope roots, and ONE batched subtree closure. No N+1.
 */
@Component
public class UserScopeExpander {

    private static final Logger log = LoggerFactory.getLogger(UserScopeExpander.class);

    private final UserProjectAssignmentRepository projectAssignments;
    private final UserDepartmentScopeRepository departmentScopes;
    private final DepartmentRepository departments;

    public UserScopeExpander(UserProjectAssignmentRepository projectAssignments,
                             UserDepartmentScopeRepository departmentScopes,
                             DepartmentRepository departments) {
        this.projectAssignments = projectAssignments;
        this.departmentScopes = departmentScopes;
        this.departments = departments;
    }

    /** Resolved scope for one (user, tenant). Immutable, never null fields. */
    public record Scope(Set<UUID> projectIds, Set<UUID> departmentScope) {
        public static Scope empty() {
            return new Scope(Set.of(), Set.of());
        }
    }

    /**
     * Resolve PROJECT ids for the active (user, tenant), claim-first.
     *
     * @param claimProjectIds the JWT {@code active_project_ids} claim (may be empty)
     * @return the claim when non-empty; otherwise the user's ACTIVE project
     *         assignments from the DB; {@link Set#of()} on any error
     */
    public Set<UUID> resolveProjectIds(UUID userId, UUID tenantId, Set<UUID> claimProjectIds) {
        if (claimProjectIds != null && !claimProjectIds.isEmpty()) {
            return claimProjectIds; // IdP authoritative — preserve current behaviour.
        }
        if (userId == null || tenantId == null) {
            return Set.of();
        }
        try {
            List<UUID> ids = projectAssignments.findActiveProjectIds(userId, tenantId);
            return ids.isEmpty() ? Set.of() : Set.copyOf(ids);
        } catch (Exception ex) {
            // Fail-closed: never widen, never crash the auth path.
            log.warn("Failed to expand project assignments for userId={} tenantId={}; "
                    + "falling back to empty scope", userId, tenantId, ex);
            return Set.of();
        }
    }

    /**
     * Resolve the DEPARTMENT SUBTREE scope for the active (user, tenant),
     * claim-first. When the claim is empty, loads the user's ACTIVE scope roots
     * and expands them to the full subtree (root + descendants).
     *
     * @param claimDepartmentScope the JWT {@code department_scope} claim (may be empty)
     * @return the claim when non-empty; otherwise the expanded subtree from the
     *         DB; {@link Set#of()} on any error
     */
    public Set<UUID> resolveDepartmentScope(UUID userId, UUID tenantId,
                                            Set<UUID> claimDepartmentScope) {
        if (claimDepartmentScope != null && !claimDepartmentScope.isEmpty()) {
            return claimDepartmentScope; // IdP authoritative — preserve current behaviour.
        }
        if (userId == null || tenantId == null) {
            return Set.of();
        }
        try {
            List<UUID> roots = departmentScopes.findActiveDepartmentIds(userId, tenantId);
            if (roots.isEmpty()) {
                // No explicit scope → unscoped user → behaves exactly as today.
                return Set.of();
            }
            List<UUID> subtree = departments.findSubtreeIds(roots, tenantId);
            // Defense in depth: always include the granted roots even if a root
            // is (transiently) not returned by the closure, but only the roots
            // already validated as tenant-owned at assign time.
            if (subtree.isEmpty()) {
                return Set.copyOf(roots);
            }
            return Set.copyOf(subtree);
        } catch (Exception ex) {
            // Fail-closed: never widen, never crash the auth path.
            log.warn("Failed to expand department scope for userId={} tenantId={}; "
                    + "falling back to empty scope", userId, tenantId, ex);
            return Set.of();
        }
    }
}
