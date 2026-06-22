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
        return applyCancellation(req, ctx.userId(), null);
    }

    /**
     * SYSTEM variant of {@link #cancel(UUID)} — used by automated, server-initiated
     * flows (e.g. the one-time {@code BackfillPanelApprovalsMigration} that retires
     * stale per-{@code EVALUATION} approvals whose evaluation is now panel-wrapped).
     *
     * <p>Differs from {@link #cancel(UUID)} ONLY by skipping the user-facing gates
     * (the {@code APPROVAL_REQUEST_CANCEL} permission, the ABAC project write-gate
     * and the own-request/Project-Manager ownership check): a system migration has
     * no acting user and cannot satisfy those — but the active tenant context IS
     * required (the request is resolved {@code findByIdAndTenantId}, anti-BOLA, and
     * the RLS GUC is bound by the surrounding {@code @Transactional}). Everything
     * else is identical to {@link #cancel(UUID)} — the {@code PENDING}-only guard,
     * the {@code PENDING -> CANCELLED} transition, and the
     * {@code APPROVAL_REQUEST_CANCELLED} audit (here carrying the supplied
     * {@code reason}) — via the shared {@link #applyCancellation} path. Mirrors the
     * {@code create}/{@code createSystem} pattern on
     * {@link uz.hrlab.grading.approval.application.CreateApprovalRequestUseCase}.
     *
     * <p>Idempotent: a request that is no longer {@code PENDING} (already cancelled
     * on a prior run / decided) is a no-op (returns {@code null}); the caller need
     * not pre-check.
     *
     * @param requestId the request to cancel (tenant-resolved from the active context)
     * @param reason    audit reason recorded on the cancellation
     * @return the cancelled request, or {@code null} if it was not PENDING (no-op)
     */
    @Transactional
    public ApprovalRequest cancelSystem(UUID requestId, String reason) {
        TenantContext ctx = TenantContextHolder.requireActive();
        ApprovalRequestJpaEntity req = requests.findByIdAndTenantId(requestId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (req.getCurrentStatus() != ApprovalRequestStatus.PENDING) {
            // Idempotent: already cancelled/decided on a prior run — nothing to do.
            return null;
        }
        return applyCancellation(req, ctx.userId(), reason);
    }

    /**
     * Shared cancellation body for {@link #cancel(UUID)} and
     * {@link #cancelSystem(UUID, String)} — flips {@code PENDING -> CANCELLED},
     * persists, and writes the standard {@code APPROVAL_REQUEST_CANCELLED} audit.
     * The caller has already loaded the tenant-scoped request and asserted it is
     * {@code PENDING}; no security re-check here (each caller applies its own).
     */
    private ApprovalRequest applyCancellation(ApprovalRequestJpaEntity req,
                                              UUID actorUserId, String reason) {
        req.setCurrentStatus(ApprovalRequestStatus.CANCELLED);
        requests.save(req);

        audit.record(AuditEvent.builder()
                .tenantId(req.getTenantId())
                .projectId(req.getProjectId())
                .actorUserId(actorUserId)
                .action(AuditAction.APPROVAL_REQUEST_CANCELLED)
                .entityType("ApprovalRequest")
                .entityId(req.getId())
                .reason(reason)
                .build());

        return queries.hydrate(req);
    }
}
