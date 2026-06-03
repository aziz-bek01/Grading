package uz.hrlab.grading.approval.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import uz.hrlab.grading.approval.domain.ApprovalRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApprovalRequestResponse(
        UUID id,
        UUID projectId,
        String entityType,
        UUID entityId,
        UUID requestedBy,
        OffsetDateTime requestedAt,
        String currentStatus,
        Map<String, String> notesI18n,
        List<ApprovalStepResponse> steps
) {
    public static ApprovalRequestResponse from(ApprovalRequest r) {
        return new ApprovalRequestResponse(
                r.id(), r.projectId(), r.entityType().name(), r.entityId(),
                r.requestedBy(), r.requestedAt(), r.currentStatus().name(),
                r.notesI18n(),
                r.steps().stream().map(ApprovalStepResponse::from).toList());
    }
}
