package uz.hrlab.grading.approval.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.approval.domain.ApprovalRequest;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestRepository;
import uz.hrlab.grading.approval.infrastructure.ApprovalStepJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalStepRepository;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Inbox — approval requests where the current user has a pending step.
 */
@Service
public class ListMyPendingApprovalsQuery {

    private final ApprovalStepRepository steps;
    private final ApprovalRequestRepository requests;
    private final ApprovalQueries queries;

    public ListMyPendingApprovalsQuery(ApprovalStepRepository steps,
                                       ApprovalRequestRepository requests,
                                       ApprovalQueries queries) {
        this.steps = steps;
        this.requests = requests;
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> list() {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.APPROVAL_REQUEST_DECIDE)) {
            throw new PermissionDeniedException();
        }
        List<String> perms = ctx.permissions() == null
                ? List.of()
                : new ArrayList<>(ctx.permissions());
        List<ApprovalStepJpaEntity> myPending = steps.findInbox(ctx.tenantId(), ctx.userId(), perms);

        Set<UUID> requestIds = new HashSet<>();
        for (var s : myPending) requestIds.add(s.getApprovalRequestId());

        List<ApprovalRequest> out = new ArrayList<>(requestIds.size());
        for (UUID requestId : requestIds) {
            ApprovalRequestJpaEntity req = requests
                    .findByIdAndTenantId(requestId, ctx.tenantId()).orElse(null);
            if (req != null && req.getCurrentStatus()
                    == uz.hrlab.grading.approval.domain.ApprovalRequestStatus.PENDING) {
                out.add(queries.hydrate(req));
            }
        }
        return out;
    }
}
