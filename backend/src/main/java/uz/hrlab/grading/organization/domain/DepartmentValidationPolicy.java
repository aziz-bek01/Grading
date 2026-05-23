package uz.hrlab.grading.organization.domain;

import uz.hrlab.grading.common.exception.ValidationException;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Pure domain policy enforcing department hierarchy invariants:
 *
 * <ol>
 *   <li>parent cannot equal self (no self-loop)</li>
 *   <li>parent must be in same tenant + project (cross-tenant probe protection)</li>
 *   <li>moving a department under one of its own descendants is forbidden
 *       (cycle prevention)</li>
 * </ol>
 *
 * <p>The repository lookup is injected as a function so the policy stays
 * dependency-free.
 */
public final class DepartmentValidationPolicy {

    public void validateParentForCreate(UUID tenantId, UUID projectId, UUID parentId,
                                        Function<UUID, Department> parentLookup) {
        if (parentId == null) return;
        Department parent = parentLookup.apply(parentId);
        if (parent == null) {
            throw new ValidationException("DEPARTMENT_PARENT_NOT_FOUND",
                    "Parent department not found in this project");
        }
        if (!parent.tenantId().equals(tenantId) || !parent.projectId().equals(projectId)) {
            // Treat as not found — never reveal cross-tenant existence.
            throw new ValidationException("DEPARTMENT_PARENT_NOT_FOUND",
                    "Parent department not found in this project");
        }
        if (parent.status() == DepartmentStatus.ARCHIVED) {
            throw new ValidationException("DEPARTMENT_PARENT_ARCHIVED",
                    "Cannot attach to an archived parent department");
        }
    }

    public void validateParentForUpdate(UUID departmentId, UUID tenantId, UUID projectId,
                                        UUID newParentId,
                                        Function<UUID, Department> parentLookup,
                                        Function<UUID, java.util.List<Department>> descendantLookup) {
        if (newParentId == null) return;
        if (newParentId.equals(departmentId)) {
            throw new ValidationException("DEPARTMENT_PARENT_SELF",
                    "A department cannot be its own parent");
        }
        validateParentForCreate(tenantId, projectId, newParentId, parentLookup);

        // Cycle check: newParent must not be in the descendants of this department.
        Set<UUID> visited = new HashSet<>();
        for (Department d : descendantLookup.apply(departmentId)) {
            if (visited.add(d.id()) && d.id().equals(newParentId)) {
                throw new ValidationException("DEPARTMENT_CYCLE",
                        "Cannot move department under its own descendant");
            }
        }
    }
}
