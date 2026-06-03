package uz.hrlab.grading.integration.exports.api;

import uz.hrlab.grading.integration.exports.domain.ExportFormat;
import uz.hrlab.grading.integration.exports.domain.ExportJobStatus;
import uz.hrlab.grading.integration.exports.domain.ExportType;
import uz.hrlab.grading.integration.exports.infrastructure.ExportJobJpaEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExportJobResponse(
        UUID id,
        UUID projectId,
        ExportType exportType,
        ExportFormat format,
        ExportJobStatus status,
        UUID requestedBy,
        OffsetDateTime requestedAt,
        OffsetDateTime generatedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime downloadedAt,
        Integer rowCount,
        Long fileSize,
        boolean containsSalaryData,
        boolean containsPersonalData,
        String traceId) {

    public static ExportJobResponse from(ExportJobJpaEntity e) {
        return new ExportJobResponse(
                e.getId(), e.getProjectId(), e.getExportType(), e.getFormat(), e.getStatus(),
                e.getRequestedBy(), e.getRequestedAt(), e.getGeneratedAt(), e.getExpiresAt(),
                e.getDownloadedAt(), e.getRowCount(), e.getFileSize(),
                e.isContainsSalaryData(), e.isContainsPersonalData(), e.getTraceId());
    }
}
