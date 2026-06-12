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
        // BE-4 — server-resolved display name for approverUserId. Wire key:
        // approver_name (NON_NULL: omitted when the user is unknown / non-member,
        // FE then falls back to the short id).
        String approverName,
        String requiredPermission,
        String status,
        UUID decidedBy,
        // BE-4 — server-resolved display name for decidedBy. Wire key:
        // decided_by_name (NON_NULL: omitted when unknown / non-member).
        String decidedByName,
        OffsetDateTime decidedAt,
        Map<String, String> reasonI18n
) {
    /** Build a step response without resolved actor names (write paths). */
    public static ApprovalStepResponse from(ApprovalStep s) {
        return from(s, null, null);
    }

    /** Build a step response with resolved approver / decider names (BE-4). */
    public static ApprovalStepResponse from(ApprovalStep s, String approverName, String decidedByName) {
        return new ApprovalStepResponse(s.id(), s.stepOrder(), s.approverUserId(),
                approverName, s.requiredPermission(), s.status().name(), s.decidedBy(),
                decidedByName, s.decidedAt(), s.reasonI18n());
    }
}
