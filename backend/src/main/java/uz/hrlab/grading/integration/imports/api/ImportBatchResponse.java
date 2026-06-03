package uz.hrlab.grading.integration.imports.api;

import uz.hrlab.grading.integration.imports.domain.ImportBatchStatus;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchJpaEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportBatchResponse(
        UUID id,
        UUID projectId,
        String templateCode,
        ImportBatchStatus status,
        String originalFilename,
        Long fileSize,
        String fileChecksum,
        Integer totalRowCount,
        Integer errorRowCount,
        Integer warningRowCount,
        Integer committedRowCount,
        boolean containsSalaryData,
        UUID uploadedBy,
        OffsetDateTime uploadedAt,
        UUID committedBy,
        OffsetDateTime committedAt,
        String traceId) {

    public static ImportBatchResponse from(ImportBatchJpaEntity e) {
        return new ImportBatchResponse(
                e.getId(), e.getProjectId(), e.getTemplateCode(), e.getStatus(),
                e.getOriginalFilename(), e.getFileSize(), e.getFileChecksum(),
                e.getTotalRowCount(), e.getErrorRowCount(), e.getWarningRowCount(),
                e.getCommittedRowCount(), e.isContainsSalaryData(),
                e.getUploadedBy(), e.getUploadedAt(),
                e.getCommittedBy(), e.getCommittedAt(), e.getTraceId());
    }
}
