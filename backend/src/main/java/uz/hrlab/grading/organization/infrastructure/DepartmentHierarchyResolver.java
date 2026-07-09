package uz.hrlab.grading.organization.infrastructure;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BE-032 — single source of truth for the batch-loading department-hierarchy
 * operations that were copy-pasted (~60 lines each) between the panel list
 * surface ({@code PanelQueries}) and the evaluation report port
 * ({@code DefaultReportDataPort}). It exposes:
 *
 * <ul>
 *   <li>{@link #loadClosure} — load the department CLOSURE (leaf departments +
 *       every ancestor up to the root), batched + tenant-scoped, no per-row N+1;
 *   <li>{@link #topLevelAncestor} — walk {@code parentId} up to the root within a
 *       loaded closure;
 *   <li>{@link #split} — the Departament (top-level ancestor) / Bo'limi (own leaf
 *       when nested) split.
 * </ul>
 *
 * <p>Every query pins {@code tenant_id} via {@code findAllByTenantIdAndIdIn}, so a
 * cross-tenant parent contributes nothing. A safety bound (64 levels) makes a
 * corrupt cyclic {@code parent_id} chain terminate instead of looping forever.
 */
@Component
public class DepartmentHierarchyResolver {

    /** Bound on closure depth / ancestor walk — org trees are shallow. */
    private static final int MAX_DEPTH = 64;

    private final DepartmentRepository departments;

    public DepartmentHierarchyResolver(DepartmentRepository departments) {
        this.departments = departments;
    }

    /**
     * Load the department CLOSURE: the leaf departments plus every ancestor up to
     * the root. Each level resolves the as-yet-unseen parent ids in ONE
     * {@code findAllByTenantIdAndIdIn} call (org trees are shallow → a handful of
     * round-trips at most, no per-row N+1). {@code tenantId} is pinned in every
     * query, so a cross-tenant parent contributes nothing; the depth bound makes a
     * corrupt cyclic {@code parent_id} chain terminate.
     */
    public Map<UUID, DepartmentJpaEntity> loadClosure(UUID tenantId, Set<UUID> leafIds) {
        if (tenantId == null || leafIds == null || leafIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, DepartmentJpaEntity> byId = new HashMap<>();
        Set<UUID> frontier = new LinkedHashSet<>(leafIds);
        int safety = 0;
        while (!frontier.isEmpty() && safety++ < MAX_DEPTH) {
            List<DepartmentJpaEntity> loaded = departments.findAllByTenantIdAndIdIn(tenantId, frontier);
            for (DepartmentJpaEntity d : loaded) {
                byId.put(d.getId(), d);
            }
            Set<UUID> nextParents = new LinkedHashSet<>();
            for (DepartmentJpaEntity d : loaded) {
                UUID parent = d.getParentId();
                if (parent != null && !byId.containsKey(parent)) {
                    nextParents.add(parent);
                }
            }
            frontier = nextParents;
        }
        return byId;
    }

    /**
     * Walk {@code parentId} up from {@code leafId} to the root within
     * {@code closure}. Returns the top-level ancestor id (== {@code leafId} when
     * the leaf has no parent). Null leaf ⇒ null. An ancestor missing from the
     * closure (cross-tenant / pruned) stops the walk at the last resolved node; a
     * {@code visited} guard makes a cyclic chain terminate.
     */
    public UUID topLevelAncestor(UUID leafId, Map<UUID, DepartmentJpaEntity> closure) {
        if (leafId == null) {
            return null;
        }
        UUID current = leafId;
        Set<UUID> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            DepartmentJpaEntity d = closure.get(current);
            if (d == null) {
                return current; // last resolved node is the best-known root
            }
            UUID parent = d.getParentId();
            if (parent == null) {
                return current;
            }
            current = parent;
        }
        return current;
    }

    /**
     * Compute the Departament (top-level ancestor) / Bo'limi (own leaf when
     * nested) split for a leaf department id within a loaded closure. The
     * {@code divisionId} is the leaf itself ONLY when it is nested under (differs
     * from) the top-level ancestor; otherwise {@code null}. Callers map the
     * resulting ids to their own representation (raw {@code name_i18n} map or a
     * localized string).
     */
    public DepartmentSplit split(UUID leafDeptId, Map<UUID, DepartmentJpaEntity> closure) {
        UUID topLevelId = topLevelAncestor(leafDeptId, closure);
        UUID divisionId = (leafDeptId != null && topLevelId != null && !leafDeptId.equals(topLevelId))
                ? leafDeptId : null;
        return new DepartmentSplit(topLevelId, divisionId);
    }

    /**
     * Result of {@link #split}: the top-level ancestor id (Departament) and the
     * division id (Bo'limi) — the latter present only when the leaf department is
     * nested under a different top-level ancestor.
     */
    public record DepartmentSplit(UUID topLevelId, UUID divisionId) { }
}
