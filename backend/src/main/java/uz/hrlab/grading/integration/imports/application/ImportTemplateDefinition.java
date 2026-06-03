package uz.hrlab.grading.integration.imports.application;

import java.util.List;

/**
 * Definition of an import template — required columns, optional columns,
 * required permission code, target entity, and whether the import is salary-
 * bearing.
 */
public record ImportTemplateDefinition(
        String templateCode,
        String displayName,
        List<String> requiredColumns,
        List<String> optionalColumns,
        List<String> requiredFields,        // for level-3 row validation
        List<String> userInputFields,       // fields that may carry user text (formula-injection candidates)
        String requiredPermission,
        String targetEntityType,
        boolean containsSalaryData
) { }
