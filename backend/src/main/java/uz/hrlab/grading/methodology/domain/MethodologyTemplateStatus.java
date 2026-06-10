package uz.hrlab.grading.methodology.domain;

/**
 * Lifecycle of a tenant-defined (DB-backed) CUSTOM methodology template
 * (Epic E). Mirrors the {@code status} CHECK on {@code methodology_templates}
 * (032-create-methodology-templates.yaml).
 *
 * <ul>
 *   <li>{@code ACTIVE}   — selectable in the create-from-template picker.</li>
 *   <li>{@code ARCHIVED} — hidden from the picker but retained (soft delete;
 *       the row keeps its frozen {@code factors_snapshot} for audit/forensics).</li>
 * </ul>
 *
 * <p>Built-in templates (CLASSIC_8_FACTOR / EXTENDED_11_CRITERIA / CUSTOM) are
 * NOT stored in this table — they live in {@code MethodologyTemplateRegistry}
 * and are always available (global, read-only); they have no status.
 */
public enum MethodologyTemplateStatus {
    ACTIVE,
    ARCHIVED
}
