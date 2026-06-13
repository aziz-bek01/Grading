package uz.hrlab.grading.organization.api;

import uz.hrlab.grading.organization.application.DepartmentPositionCount;

import java.util.UUID;

/**
 * Defect-1 BE — wire shape for the server-authoritative position counts endpoint.
 * snake_case is applied by the global Jackson naming strategy.
 *
 * <ul>
 *   <li>{@code direct_count} — ACTIVE positions directly in this department.</li>
 *   <li>{@code subtree_count} — self + all descendants (so a parent rolls up its
 *       children, and the count is never truncated by a position page cap).</li>
 * </ul>
 */
public record DepartmentPositionCountResponse(UUID departmentId, long directCount, long subtreeCount) {

    public static DepartmentPositionCountResponse from(DepartmentPositionCount c) {
        return new DepartmentPositionCountResponse(
                c.departmentId(), c.directCount(), c.subtreeCount());
    }
}
