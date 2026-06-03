package uz.hrlab.grading.reporting.api;

import uz.hrlab.grading.reporting.domain.ReportFormat;
import uz.hrlab.grading.reporting.domain.ReportStatus;
import uz.hrlab.grading.reporting.domain.ReportType;
import uz.hrlab.grading.reporting.infrastructure.ReportJpaEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        UUID projectId,
        ReportType reportType,
        ReportFormat format,
        ReportStatus status,
        String title,
        String locale,
        UUID requestedBy,
        OffsetDateTime requestedAt,
        OffsetDateTime generatedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime downloadedAt,
        Long fileSize,
        boolean containsSalaryData,
        boolean containsPersonalData,
        int attemptCount,
        String failureReason,
        String traceId) {

    public static ReportResponse from(ReportJpaEntity e) {
        return new ReportResponse(
                e.getId(), e.getProjectId(), e.getReportType(), e.getFormat(), e.getStatus(),
                e.getTitle(), e.getLocale(),
                e.getRequestedBy(), e.getRequestedAt(), e.getGeneratedAt(),
                e.getExpiresAt(), e.getDownloadedAt(), e.getFileSize(),
                e.isContainsSalaryData(), e.isContainsPersonalData(),
                e.getAttemptCount(), e.getFailureReason(), e.getTraceId());
    }
}
