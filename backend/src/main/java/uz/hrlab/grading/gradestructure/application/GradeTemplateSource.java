package uz.hrlab.grading.gradestructure.application;

/**
 * Provenance of a grade template in the unified catalog (BE-8). Mirrors the
 * methodology {@code TemplateSource}.
 *
 * <ul>
 *   <li>{@code BUILTIN} — global, read-only skeleton from
 *       {@code GradeStructureTemplateRegistry} (GRADE_14 / GRADE_16 / CUSTOM).
 *       Cannot be edited, archived, or shadowed by a custom code.</li>
 *   <li>{@code CUSTOM} — tenant-defined, DB-backed row in
 *       {@code grade_templates}, produced by "save grade structure as template".
 *       Editable/archivable by the owning tenant only.</li>
 * </ul>
 */
public enum GradeTemplateSource {
    BUILTIN,
    CUSTOM
}
