package uz.hrlab.grading.integration.validation;

import uz.hrlab.grading.integration.imports.domain.ImportErrorLevel;

/**
 * One validation finding (integration-blueprint §12). Stateless POJO so
 * validators can be safely shared as Spring beans.
 */
public record ValidationError(
        ImportErrorLevel level,
        String code,
        Integer rowNumber,
        String fieldName,
        String message,
        String suggestedFix,
        String technicalDetails) {

    public static ValidationError blocker(String code, String message) {
        return new ValidationError(ImportErrorLevel.BLOCKER, code, null, null, message, null, null);
    }

    public static ValidationError rowError(int rowNumber, String code, String fieldName,
                                           String message, String suggestedFix) {
        return new ValidationError(ImportErrorLevel.ERROR, code, rowNumber, fieldName, message, suggestedFix, null);
    }

    public static ValidationError rowWarning(int rowNumber, String code, String fieldName,
                                             String message) {
        return new ValidationError(ImportErrorLevel.WARNING, code, rowNumber, fieldName, message, null, null);
    }
}
