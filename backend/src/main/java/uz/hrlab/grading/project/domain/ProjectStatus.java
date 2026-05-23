package uz.hrlab.grading.project.domain;

/**
 * Lifecycle status of a grading project (architecture §14, §9 row "Project").
 *
 * <ul>
 *   <li>{@code DRAFT} — created, not yet active.</li>
 *   <li>{@code ACTIVE} — business operations allowed.</li>
 *   <li>{@code LOCKED} — read-only for business operations
 *       (PATCH/DELETE rejected with 409 at service layer).</li>
 *   <li>{@code ARCHIVED} — soft-archived; not visible by default.</li>
 * </ul>
 */
public enum ProjectStatus {
    DRAFT,
    ACTIVE,
    LOCKED,
    ARCHIVED
}
