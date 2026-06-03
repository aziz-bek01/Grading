package uz.hrlab.grading.approval.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import uz.hrlab.grading.approval.domain.ApprovalStep;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApprovalStepResponse(
        UUID id,
        int stepOrder,
        UUID approverUserId,
        String requiredPermission,
        String status,
        UUID decidedBy,
        OffsetDateTime decidedAt,
        Map<String, String> reasonI18n
) {
    public static ApprovalStepResponse from(ApprovalStep s) {
        return new ApprovalStepResponse(s.id(), s.stepOrder(), s.approverUserId(),
                s.requiredPermission(), s.status().name(), s.decidedBy(), s.decidedAt(),
                s.reasonI18n());
    }
}
