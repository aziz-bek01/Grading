package uz.hrlab.grading.access.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.infrastructure.UserJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.integration.idp.application.IdentityProvisioningException;
import uz.hrlab.grading.integration.idp.application.IdentityProvisioningPort;
import uz.hrlab.grading.integration.idp.infrastructure.ZitadelIdpProperties;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
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
 *
 * <h3>IdP offboarding (decision doc 08, US-3)</h3>
 * Revoking a membership cuts the user off from THIS tenant. We deactivate the
 * shared ZITADEL login ONLY when this was the user's LAST active membership in
 * ANY tenant — a user who still belongs to another company must keep their
 * login. Computed AFTER this revoke from {@code findAllByUserId}. The IdP call
 * is gated on {@code grading.idp.zitadel.enabled=true} AND a non-null
 * {@code external_idp_subject}; otherwise it is a no-op.
 *
 * <p><b>Consistency decision (deliberate, mirrors {@code PatchUserUseCase}):</b>
 * the in-app revoke is committed first and is the primary security effect; the
 * IdP deactivate is best-effort. On IdP failure we audit
 * {@code USER_IDP_DEACTIVATION_FAILED} (REQUIRES_NEW) and WARN, but do NOT roll
 * back the revoke — in-app access is already cut and the deactivate is
 * idempotent-friendly for a later retry.
 */
@Service
public class RevokeMembershipUseCase {

    private static final Logger log = LoggerFactory.getLogger(RevokeMembershipUseCase.class);

    private final UserManagementPolicy policy;
    private final UserTenantMembershipRepository membershipRepo;
    private final UserRepository userRepo;
    private final AuditService audit;
    private final IdentityProvisioningPort identityProvisioning;
    private final ZitadelIdpProperties idpProps;

    public RevokeMembershipUseCase(UserManagementPolicy policy,
                                   UserTenantMembershipRepository membershipRepo,
                                   UserRepository userRepo,
                                   AuditService audit,
                                   IdentityProvisioningPort identityProvisioning,
                                   ZitadelIdpProperties idpProps) {
        this.policy = policy;
        this.membershipRepo = membershipRepo;
        this.userRepo = userRepo;
        this.audit = audit;
        this.identityProvisioning = identityProvisioning;
        this.idpProps = idpProps;
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

        // IdP offboarding: deactivate the shared login ONLY if the user has NO
        // remaining ACTIVE membership in ANY tenant after this revoke. A user who
        // still belongs to another company keeps their login untouched.
        maybeDeactivateIdpIfLastMembership(ctx, userId, membership.getId());
    }

    /**
     * Deactivate the IdP login when, after the revoke, the user has zero ACTIVE
     * memberships left across all tenants. NO-OP when the flag is off or the user
     * was never provisioned. Best-effort: in-app revoke already stands, so an IdP
     * failure is audited ({@code USER_IDP_DEACTIVATION_FAILED}, REQUIRES_NEW) and
     * logged but never rethrown.
     */
    private void maybeDeactivateIdpIfLastMembership(TenantContext ctx, UUID userId,
                                                    UUID revokedMembershipId) {
        if (!idpProps.isEnabled()) {
            return;
        }
        // Re-read all memberships; the just-revoked row is REVOKED in this TX.
        // Defensively exclude it by id in case the read returns a stale snapshot.
        List<UserTenantMembershipJpaEntity> all = membershipRepo.findAllByUserId(userId);
        boolean hasOtherActive = all.stream()
                .anyMatch(m -> !m.getId().equals(revokedMembershipId)
                        && m.getStatus() == MembershipStatus.ACTIVE);
        if (hasOtherActive) {
            return; // still belongs to another company — keep the login
        }

        UserJpaEntity user = userRepo.findById(userId).orElse(null);
        String subject = user == null ? null : user.getExternalIdpSubject();
        if (subject == null || subject.isBlank()) {
            return; // never provisioned via the new flow — nothing to deactivate
        }

        try {
            identityProvisioning.deactivateUser(subject);
            audit.record(AuditEvent.builder(ctx)
                    .action(AuditAction.USER_IDP_ACCOUNT_DEACTIVATED)
                    .entityType("User")
                    .entityId(userId)
                    .reason("externalIdpSubject=" + subject + " trigger=LAST_MEMBERSHIP_REVOKED")
                    .build());
        } catch (IdentityProvisioningException ex) {
            audit.record(AuditEvent.builder(ctx)
                    .action(AuditAction.USER_IDP_DEACTIVATION_FAILED)
                    .entityType("User")
                    .entityId(userId)
                    .reason("externalIdpSubject=" + subject
                            + " trigger=LAST_MEMBERSHIP_REVOKED reason=" + ex.getCode())
                    .build());
            log.warn("IdP deactivate failed after last-membership revoke; in-app revoke stands, "
                    + "flagged for retry. subject={} code={}", subject, ex.getCode());
        }
    }
}
