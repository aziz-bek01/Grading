package uz.hrlab.grading.approval.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.approval.domain.ApprovalRequest;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.approval.domain.ApprovalTransitionRejectedException;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * Requestor cancels their own PENDING request. Anyone with
 * APPROVAL_REQUEST_CANCEL can cancel — but ABAC + ownership check below
 * narrows to "own request only" (Project Manager can override via separate
 * future use case).
 */
@Service
public class CancelApprovalRequestUseCase {

    private final ApprovalRequestRepository requests;
    private final ApprovalQueries queries;
    private final AbacGate abacGate;
    private final AuditService audit;

    public CancelApprovalRequestUseCase(ApprovalRequestRepository requests,
                                        ApprovalQueries queries,
                                        AbacGate abacGate,
                                        AuditService audit) {
        this.requests = requests;
        this.queries = queries;
        this.abacGate = abacGate;
        this.audit = audit;
    }

    @Transactional
    public ApprovalRequest cancel(UUID requestId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.APPROVAL_REQUEST_CANCEL)) {
            throw new PermissionDeniedException();
        }
        ApprovalRequestJpaEntity req = requests.findByIdAndTenantId(requestId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanWriteInProject(ctx, req.getProjectId());

        // Own-only — Project Manager override (PROJECT_EDIT) skips the check.
        boolean isOwner = ctx.userId() != null && ctx.userId().equals(req.getRequestedBy());
        boolean isProjectManager = ctx.hasPermission(PermissionCodes.PROJECT_EDIT);
        if (!isOwner && !isProjectManager) {
            throw new PermissionDeniedException();
        }
        if (req.getCurrentStatus() != ApprovalRequestStatus.PENDING) {
            throw new ApprovalTransitionRejectedException(
                    "REQUEST_NOT_PENDING",
                    "Only pending requests can be cancelled");
        }
        req.setCurrentStatus(ApprovalRequestStatus.CANCELLED);
        requests.save(req);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(req.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.APPROVAL_REQUEST_CANCELLED)
                .entityType("ApprovalRequest")
                .entityId(req.getId())
                .build());

        return queries.hydrate(req);
    }
}
