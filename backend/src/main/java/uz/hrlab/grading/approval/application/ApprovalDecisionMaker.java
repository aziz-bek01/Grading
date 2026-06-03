package uz.hrlab.grading.approval.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.approval.domain.ApprovalDecisionType;
import uz.hrlab.grading.approval.domain.ApprovalRequest;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.approval.domain.ApprovalStepStatus;
import uz.hrlab.grading.approval.domain.ApprovalTransitionRejectedException;
import uz.hrlab.grading.approval.infrastructure.ApprovalDecisionJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalDecisionRepository;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestRepository;
import uz.hrlab.grading.approval.infrastructure.ApprovalStepJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalStepRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Shared transition core for approve / reject / request-changes. Encapsulates
 * the FSM rules in one place so the three use cases stay tiny.
 */
@Service
public class ApprovalDecisionMaker {

    private static final int MIN_REASON_LENGTH = 20;

    private final ApprovalRequestRepository requests;
    private final ApprovalStepRepository steps;
    private final ApprovalDecisionRepository decisions;
    private final ApprovalQueries queries;
    private final AbacGate abacGate;
    private final AuditService audit;

    public ApprovalDecisionMaker(ApprovalRequestRepository requests,
                                 ApprovalStepRepository steps,
                                 ApprovalDecisionRepository decisions,
                                 ApprovalQueries queries,
                                 AbacGate abacGate,
                                 AuditService audit) {
        this.requests = requests;
        this.steps = steps;
        this.decisions = decisions;
        this.queries = queries;
        this.abacGate = abacGate;
        this.audit = audit;
    }

    @Transactional
    public ApprovalRequest approve(UUID requestId, UUID stepId, String notes) {
        return decide(requestId, stepId, ApprovalDecisionType.APPROVE, notes);
    }

    @Transactional
    public ApprovalRequest reject(UUID requestId, UUID stepId, String reason) {
        validateReason(reason);
        return decide(requestId, stepId, ApprovalDecisionType.REJECT, reason);
    }

    @Transactional
    public ApprovalRequest requestChanges(UUID requestId, UUID stepId, String reason) {
        validateReason(reason);
        return decide(requestId, stepId, ApprovalDecisionType.REQUEST_CHANGES, reason);
    }

    private ApprovalRequest decide(UUID requestId, UUID stepId,
                                   ApprovalDecisionType decisionType, String reasonOrNotes) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.APPROVAL_REQUEST_DECIDE)) {
            throw new PermissionDeniedException();
        }
        ApprovalRequestJpaEntity req = requests.findByIdAndTenantId(requestId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanWriteInProject(ctx, req.getProjectId());

        if (req.getCurrentStatus() != ApprovalRequestStatus.PENDING) {
            throw new ApprovalTransitionRejectedException(
                    "REQUEST_NOT_PENDING",
                    "Approval request is in terminal status " + req.getCurrentStatus());
        }

        ApprovalStepJpaEntity step = steps.findByIdAndTenantId(stepId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (!step.getApprovalRequestId().equals(requestId)) {
            throw new TenantAccessDeniedException();
        }
        if (step.getStatus() != ApprovalStepStatus.PENDING) {
            throw new ApprovalTransitionRejectedException(
                    "STEP_NOT_PENDING", "Step is already decided");
        }

        // FSM: caller must be the assigned approver, OR the step must be open
        // (approverUserId NULL) AND caller has the required permission.
        if (step.getApproverUserId() != null) {
            if (!step.getApproverUserId().equals(ctx.userId())) {
                throw new PermissionDeniedException();
            }
        } else if (!ctx.hasPermission(step.getRequiredPermission())) {
            throw new PermissionDeniedException();
        }

        // Step status update
        OffsetDateTime now = OffsetDateTime.now();
        switch (decisionType) {
            case APPROVE          -> step.setStatus(ApprovalStepStatus.APPROVED);
            case REJECT           -> step.setStatus(ApprovalStepStatus.REJECTED);
            case REQUEST_CHANGES  -> step.setStatus(ApprovalStepStatus.REJECTED);
            case DELEGATE         -> throw new ValidationException("DELEGATION_NOT_SUPPORTED_YET");
        }
        step.setDecidedBy(ctx.userId());
        step.setDecidedAt(now);
        steps.save(step);

        // Record decision row
        decisions.save(new ApprovalDecisionJpaEntity(
                UUID.randomUUID(), ctx.tenantId(), step.getId(),
                decisionType, reasonOrNotes, ctx.userId(), now));

        // Aggregate-level status update
        String action;
        switch (decisionType) {
            case APPROVE -> {
                // If another PENDING step exists ahead, leave request PENDING.
                ApprovalStepJpaEntity nextPending = queries
                        .findCurrentPendingStep(ctx.tenantId(), requestId);
                if (nextPending == null) {
                    req.setCurrentStatus(ApprovalRequestStatus.APPROVED);
                }
                action = AuditAction.APPROVAL_STEP_APPROVED;
            }
            case REJECT -> {
                req.setCurrentStatus(ApprovalRequestStatus.REJECTED);
                action = AuditAction.APPROVAL_STEP_REJECTED;
            }
            case REQUEST_CHANGES -> {
                req.setCurrentStatus(ApprovalRequestStatus.CHANGES_REQUESTED);
                action = AuditAction.APPROVAL_STEP_CHANGES_REQUESTED;
            }
            default -> throw new ValidationException("UNSUPPORTED_DECISION");
        }
        requests.save(req);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(req.getProjectId())
                .actorUserId(ctx.userId())
                .action(action)
                .entityType("ApprovalStep")
                .entityId(step.getId())
                .reason(reasonOrNotes)
                .build());
        return queries.hydrate(req);
    }

    private static void validateReason(String reason) {
        if (reason == null || reason.trim().length() < MIN_REASON_LENGTH) {
            throw new ValidationException("REASON_TOO_SHORT");
        }
    }
}
