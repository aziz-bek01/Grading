package uz.hrlab.grading.integration.imports.api;

import uz.hrlab.grading.integration.imports.domain.ImportErrorLevel;
import uz.hrlab.grading.integration.imports.infrastructure.ImportErrorJpaEntity;

import java.util.UUID;

public record ImportErrorResponse(
        UUID id,
        UUID importBatchRowId,
        ImportErrorLevel errorLevel,
        String errorCode,
        String fieldName,
        String message,
        String suggestedFix,
        String traceId) {

    public static ImportErrorResponse from(ImportErrorJpaEntity e) {
        return new ImportErrorResponse(
                e.getId(), e.getImportBatchRowId(), e.getErrorLevel(), e.getErrorCode(),
                e.getFieldName(), e.getMessage(), e.getSuggestedFix(), e.getTraceId());
    }
}
