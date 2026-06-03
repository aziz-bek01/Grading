package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * Use case for {@code DELETE /api/v1/users/{id}/memberships/{tenantId}}.
 *
 * <p>Soft revoke — the membership row stays for audit history; status flips
 * to {@code REVOKED} and the salary-permission flag is force-cleared (so a
 * later re-activation does not silently restore a sensitive grant).
 *
 * <p>Defends against self-revoke (a user removing their last admin role from
 * themselves and locking the tenant out) — caller cannot revoke their own
 * membership.
 */
@Service
public class RevokeMembershipUseCase {

    private final UserManagementPolicy policy;
    private final UserTenantMembershipRepository membershipRepo;
    private final AuditService audit;

    public RevokeMembershipUseCase(UserManagementPolicy policy,
                                   UserTenantMembershipRepository membershipRepo,
                                   AuditService audit) {
        this.policy = policy;
        this.membershipRepo = membershipRepo;
        this.audit = audit;
    }

    @Transactional
    public void revoke(UUID userId, UUID tenantId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        policy.requireCanManageInTenant(ctx, tenantId);

        UserTenantMembershipJpaEntity membership = membershipRepo
                .findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(TenantAccessDeniedException::new);

        if (ctx.userId() != null && ctx.userId().equals(userId)) {
            throw new PermissionDeniedException("Self membership revocation is not allowed");
        }

        if (membership.getStatus() == MembershipStatus.REVOKED) {
            return; // idempotent
        }

        membership.setStatus(MembershipStatus.REVOKED);
        membership.setSalaryDataPermission(false);
        membershipRepo.save(membership);

        audit.record(AuditEvent.builder()
                .tenantId(tenantId)
                .actorUserId(ctx.userId())
                .action(AuditAction.USER_MEMBERSHIP_REVOKED)
                .entityType("UserTenantMembership")
                .entityId(membership.getId())
                .reason("userId=" + userId)
                .build());
    }
}
