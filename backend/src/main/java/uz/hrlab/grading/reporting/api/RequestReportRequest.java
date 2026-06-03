package uz.hrlab.grading.reporting.api;

import jakarta.validation.constraints.NotNull;
import uz.hrlab.grading.reporting.domain.ReportFormat;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.util.UUID;

public record RequestReportRequest(
        @NotNull ReportType reportType,
        @NotNull ReportFormat format,
        @NotNull UUID projectId,
        String filterParams) {
}
