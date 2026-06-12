package uz.hrlab.grading.approval.application;

import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;

import java.util.UUID;

/**
 * Manual coupling hook (no event bus) so a module that OWNS an approval entity
 * type can react when its approval request reaches a terminal status. The
 * {@link ApprovalDecisionMaker} invokes every registered listener AFTER it has
 * persisted the step/request decision. Implementations MUST be fail-soft — a
 * listener throwing must NOT roll back the (already valid) approval decision.
 *
 * <p>Mirrors the existing manual "approve evaluation" coupling but generalized
 * so the panel module (MVP2) can flip its panel status without the approval
 * module depending on it directly.
 */
public interface ApprovalOutcomeListener {

    /**
     * @param tenantId    server-derived active tenant
     * @param entityType  the request's entity type
     * @param entityId    the request's entity id
     * @param newStatus   the request's new (just-decided) status
     * @param actorUserId the deciding user
     */
    void onApprovalRequestDecided(UUID tenantId, ApprovalEntityType entityType, UUID entityId,
                                  ApprovalRequestStatus newStatus, UUID actorUserId);
}
