package uz.hrlab.grading.integration.imports.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.integration.imports.domain.ImportTemplateCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Catalog of supported import templates (integration-blueprint §5.1).
 *
 * <p>Templates referenced here are the ones in MVP 2 Phase 2 scope. The
 * registry is in-memory + immutable; adding a new template requires a new
 * deployment, so the validators can never be reconfigured at runtime.
 */
@Component
public class ImportTemplateRegistry {

    /**
     * Column suffixes that mark a per-locale sibling of a text column, in the
     * order they are offered in the downloadable template. Kept in sync with
     * {@code MethodologyFactorsRowCommitter.LOCALE_SUFFIXES}: {@code _uz} is the
     * friendly alias for {@code uz-Cyrl-UZ} and is the one HR actually fills in,
     * so the rarely-used {@code _uz_cyrl} alias is not shipped as a header.
     */
    private static final List<String> LOCALE_COLUMN_SUFFIXES =
            List.of("_uz", "_uz_latn", "_en");

    /**
     * METHODOLOGY_FACTORS_V1 text columns whose content is free HR prose: each
     * accepts per-locale siblings AND is a formula-injection candidate.
     */
    private static final List<String> METHODOLOGY_TEXT_COLUMNS =
            List.of("methodology_name", "factor_name", "level_name", "level_description");

    /** {@code base} → {@code base_uz}, {@code base_uz_latn}, {@code base_en}. */
    private static List<String> localeSiblings(List<String> bases) {
        return bases.stream()
                .flatMap(base -> LOCALE_COLUMN_SUFFIXES.stream().map(sfx -> base + sfx))
                .toList();
    }

    /** The base text columns plus every localized sibling of them. */
    private static List<String> userInputTextColumns(List<String> bases) {
        return Stream.concat(bases.stream(), localeSiblings(bases).stream())
                .toList();
    }

    private final Map<String, ImportTemplateDefinition> templates = new LinkedHashMap<>();

    public ImportTemplateRegistry() {
        register(new ImportTemplateDefinition(
                ImportTemplateCode.ORG_STRUCTURE_V1,
                "Organization Structure",
                List.of("external_id", "name", "parent_external_id", "level"),
                List.of("location_code"),
                List.of("external_id", "name"),
                List.of("name"),
                "ORG_IMPORT",
                "Department",
                false));
        register(new ImportTemplateDefinition(
                ImportTemplateCode.POSITION_CATALOG_V1,
                "Position Catalog",
                List.of("external_id", "title", "department_external_id", "status"),
                List.of("grade_code"),
                List.of("external_id", "title", "department_external_id"),
                List.of("title"),
                "POSITION_IMPORT",
                "Position",
                false));
        register(new ImportTemplateDefinition(
                ImportTemplateCode.JOB_PROFILE_V1,
                "Job Profile",
                List.of("position_external_id", "purpose", "responsibilities", "requirements"),
                List.of(),
                List.of("position_external_id", "purpose"),
                List.of("purpose", "responsibilities", "requirements"),
                PermissionCodes.JOB_PROFILE_EDIT,
                "JobProfile",
                false));
        register(new ImportTemplateDefinition(
                ImportTemplateCode.METHODOLOGY_FACTORS_V1,
                "Methodology Factors + Levels",
                // Methodology-level metadata (repeated on every row — one methodology
                // per file) FIRST, then the per-factor/level columns.
                List.of("methodology_code", "methodology_name", "methodology_type",
                        "scoring_mode", "target_total_points",
                        "factor_code", "factor_name", "level_code", "level_name", "weight", "score"),
                // Optional columns: the scoring extras first (so the numeric block
                // stays together), then the base description, then the per-locale
                // siblings of every text column — ONE upload fills a methodology in
                // all four supported languages instead of leaving ru-RU-only
                // content for manual translation.
                Stream.concat(
                        Stream.of("scale_value", "level_order", "level_description"),
                        localeSiblings(METHODOLOGY_TEXT_COLUMNS).stream()).toList(),
                // requiredFields (Level-3 blank-cell check) includes weight + score:
                // the committer requires both on every row, so catch a blank cell at
                // upload time rather than surfacing it as a per-row commit failure.
                List.of("methodology_code", "methodology_name", "methodology_type",
                        "scoring_mode", "target_total_points",
                        "factor_code", "factor_name", "level_code", "level_name",
                        "weight", "score"),
                // Every text column is a formula-injection candidate — including
                // the localized siblings, which carry the same free HR prose.
                userInputTextColumns(METHODOLOGY_TEXT_COLUMNS),
                "METHODOLOGY_IMPORT",
                "Methodology",
                false));
        register(new ImportTemplateDefinition(
                ImportTemplateCode.GRADE_BANDS_V1,
                "Grade Bands",
                List.of("grade_code", "min_score", "max_score", "label"),
                List.of(),
                List.of("grade_code", "min_score", "max_score"),
                List.of("label"),
                "GRADE_IMPORT",
                "GradeBand",
                false));
    }

    private void register(ImportTemplateDefinition def) {
        templates.put(def.templateCode(), def);
    }

    public Optional<ImportTemplateDefinition> find(String templateCode) {
        return Optional.ofNullable(templates.get(templateCode));
    }

    public List<ImportTemplateDefinition> all() {
        return List.copyOf(templates.values());
    }
}
