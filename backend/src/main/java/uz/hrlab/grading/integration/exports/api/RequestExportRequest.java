package uz.hrlab.grading.integration.exports.api;

import jakarta.validation.constraints.NotNull;
import uz.hrlab.grading.integration.exports.domain.ExportFormat;
import uz.hrlab.grading.integration.exports.domain.ExportType;

import java.util.UUID;

public record RequestExportRequest(
        @NotNull ExportType exportType,
        @NotNull ExportFormat format,
        @NotNull UUID projectId,
        String filterParams) {
}
