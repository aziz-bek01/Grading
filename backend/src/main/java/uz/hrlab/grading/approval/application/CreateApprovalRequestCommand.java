package uz.hrlab.grading.approval.application;

import uz.hrlab.grading.approval.domain.ApprovalEntityType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command to open an approval request. Steps are explicit: one entry per step
 * with the required permission and optional pre-assigned approver. Use the
 * {@link #singleStep} factory for the common one-step flow.
 */
public record CreateApprovalRequestCommand(
        UUID projectId,
        ApprovalEntityType entityType,
        UUID entityId,
        Map<String, String> notesI18n,
        List<StepSpec> steps
) {
    public record StepSpec(int stepOrder, UUID approverUserId, String requiredPermission) { }

    public static CreateApprovalRequestCommand singleStep(UUID projectId,
                                                          ApprovalEntityType entityType,
                                                          UUID entityId,
                                                          String requiredPermission) {
        return new CreateApprovalRequestCommand(projectId, entityType, entityId, null,
                List.of(new StepSpec(1, null, requiredPermission)));
    }
}
