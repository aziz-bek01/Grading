package uz.hrlab.grading.gradestructure.domain;

/**
 * Lifecycle of a tenant-defined (DB-backed) CUSTOM grade template (BE-7).
 * Mirrors the {@code status} CHECK on {@code grade_templates}
 * (033-create-grade-templates.yaml) and the methodology
 * {@code MethodologyTemplateStatus} pattern.
 *
 * <ul>
 *   <li>{@code ACTIVE}   — selectable in the create-from-template picker.</li>
 *   <li>{@code ARCHIVED} — hidden from the picker but retained (soft delete;
 *       the row keeps its frozen {@code grades_snapshot} for audit/forensics).</li>
 * </ul>
 *
 * <p>Built-in templates (GRADE_14 / GRADE_16 / CUSTOM) are NOT stored in this
 * table — they live in {@code GradeStructureTemplateRegistry} and are always
 * available (global, read-only); they have no status.
 */
public enum GradeTemplateStatus {
    ACTIVE,
    ARCHIVED
}
