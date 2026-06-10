package uz.hrlab.grading.methodology.application;

/**
 * Provenance of a methodology template in the unified catalog (Epic E).
 *
 * <ul>
 *   <li>{@code BUILTIN} — global, read-only skeleton from
 *       {@code MethodologyTemplateRegistry} (CLASSIC_8_FACTOR /
 *       EXTENDED_11_CRITERIA / CUSTOM). Cannot be edited, archived, or shadowed
 *       by a custom code.</li>
 *   <li>{@code CUSTOM} — tenant-defined, DB-backed row in
 *       {@code methodology_templates}, produced by "save methodology version as
 *       template". Editable/archivable by the owning tenant only.</li>
 * </ul>
 */
public enum TemplateSource {
    BUILTIN,
    CUSTOM
}
