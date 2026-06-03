package uz.hrlab.grading.approval.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateApprovalRequestRequest(
        @NotNull UUID projectId,
        @NotNull ApprovalEntityType entityType,
        @NotNull UUID entityId,
        Map<String, String> notesI18n,
        @NotEmpty @Valid List<StepRequest> steps
) {
    public record StepRequest(int stepOrder,
                              UUID approverUserId,
                              @NotBlank String requiredPermission) { }
}
