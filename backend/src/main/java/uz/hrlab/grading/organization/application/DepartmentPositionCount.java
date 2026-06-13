package uz.hrlab.grading.organization.application;

import java.util.UUID;

/**
 * Defect-1 BE — server-authoritative position counts for a single department.
 *
 * <ul>
 *   <li>{@code directCount} — ACTIVE positions whose {@code department_id} is
 *       exactly this department.</li>
 *   <li>{@code subtreeCount} — {@code directCount} of this department PLUS the
 *       direct counts of every descendant (self + subtree roll-up), so a parent
 *       correctly aggregates its children (fixes defect 1a) and the count is
 *       never truncated by a position page cap (fixes defect 1b).</li>
 * </ul>
 */
public record DepartmentPositionCount(UUID departmentId, long directCount, long subtreeCount) {
}
