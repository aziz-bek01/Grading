package uz.hrlab.grading.integration.imports.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.integration.imports.domain.ImportTemplateCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catalog of supported import templates (integration-blueprint §5.1).
 *
 * <p>Templates referenced here are the ones in MVP 2 Phase 2 scope. The
 * registry is in-memory + immutable; adding a new template requires a new
 * deployment, so the validators can never be reconfigured at runtime.
 */
@Component
public class ImportTemplateRegistry {

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
                List.of("factor_code", "factor_name", "level_code", "level_name", "weight", "score"),
                List.of(),
                List.of("factor_code", "factor_name", "level_code", "level_name"),
                List.of("factor_name", "level_name"),
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
