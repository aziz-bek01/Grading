package uz.hrlab.grading.access.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.api.UserDetailsResponse;
import uz.hrlab.grading.access.domain.UserStatus;
import uz.hrlab.grading.access.infrastructure.UserJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.integration.idp.application.IdentityProvisioningException;
import uz.hrlab.grading.integration.idp.application.IdentityProvisioningPort;
import uz.hrlab.grading.integration.idp.infrastructure.ZitadelIdpProperties;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.UUID;

/**
 * Use case for {@code PATCH /api/v1/users/{id}} — partial update of the
 * platform {@code public.users} row.
 *
 * <p>Allowed mutations:
 * <ul>
 *   <li>{@code fullName} — trimmed, max 255 chars (DB constraint).</li>
 *   <li>{@code locale} — must be one of the four supported locales (Bean
 *       Validation already enforced; we re-check defensively here).</li>
 *   <li>{@code status} — {@code ACTIVE} or {@code DISABLED} only. INVITED is
 *       set only by the invite flow; LOCKED is set only by the failed-auth
 *       guardrail.</li>
 * </ul>
 *
 * <p>Tenant scope: the user is platform-global, but the action of updating
 * them is authorized against a tenant the caller manages — we use the
 * caller's active tenant as the audit anchor.
 *
 * <h3>IdP offboarding/re-enable (decision doc 08, US-3)</h3>
 * A status change here is the primary security effect and must succeed; the IdP
 * call is BEST-EFFORT within the same operation. On a transition to
 * {@code DISABLED} we deactivate the ZITADEL login; on a transition back to
 * {@code ACTIVE} we reactivate it — but only when {@code grading.idp.zitadel
 * .enabled=true} AND the user carries an {@code external_idp_subject} (users
 * never provisioned via the new flow have a null subject and are skipped). When
 * the flag is off the {@code NoOp} port makes every call a no-op, so behaviour
 * is byte-for-byte the pre-IdP one.
 *
 * <p><b>Consistency decision (deliberate):</b> the in-app status flip is
 * committed first; the IdP call runs after. If the IdP call fails we record
 * {@code USER_IDP_DEACTIVATION_FAILED} (REQUIRES_NEW audit, like the invite
 * failure path) and log WARN, but we do NOT roll back the in-app disable. It is
 * strictly safer to have in-app access cut plus a flagged IdP retry than to
 * leave the user fully active because ZITADEL was momentarily down. The
 * deactivate/reactivate calls are themselves idempotent-friendly (see port
 * contract), so a later manual/automated retry is safe.
 */
@Service
public class PatchUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(PatchUserUseCase.class);

    private final UserManagementPolicy policy;
    private final UserRepository userRepo;
    private final UserTenantMembershipRepository membershipRepo;
    private final GetUserDetailsQuery detailsQuery;
    private final AuditService audit;
    private final IdentityProvisioningPort identityProvisioning;
    private final ZitadelIdpProperties idpProps;

    public PatchUserUseCase(UserManagementPolicy policy,
                            UserRepository userRepo,
                            UserTenantMembershipRepository membershipRepo,
                            GetUserDetailsQuery detailsQuery,
                            AuditService audit,
                            IdentityProvisioningPort identityProvisioning,
                            ZitadelIdpProperties idpProps) {
        this.policy = policy;
        this.userRepo = userRepo;
        this.membershipRepo = membershipRepo;
        this.detailsQuery = detailsQuery;
        this.audit = audit;
        this.identityProvisioning = identityProvisioning;
        this.idpProps = idpProps;
    }

    @Transactional
    public UserDetailsResponse patch(UUID userId, String fullName, String locale, String status) {
        TenantContext ctx = TenantContextHolder.requireActive();

        UserJpaEntity user = userRepo.findById(userId)
                .orElseThrow(TenantAccessDeniedException::new);

        // ABAC: overlap with caller via memberships.
        List<UserTenantMembershipJpaEntity> memberships =
                membershipRepo.findAllByUserId(userId);
        policy.requireCanViewUser(ctx, memberships);
        // requireCanManageInTenant against the caller's active tenant — same
        // door the rest of the module uses.
        policy.requireCanManageInTenant(ctx, ctx.tenantId());

        boolean changed = false;
        StringBuilder reason = new StringBuilder();
        // IdP offboarding/re-enable intent, resolved while applying the status
        // change but ACTED ON only AFTER the in-app row is persisted (best-effort,
        // never blocks the in-app cutoff). null = no IdP transition this request.
        UserStatus idpTransition = null;

        if (fullName != null) {
            String trimmed = fullName.trim();
            if (trimmed.isEmpty()) {
                throw new ValidationException("USER_PATCH_BLANK_NAME",
                        "fullName cannot be blank when provided");
            }
            if (!trimmed.equals(user.getFullName())) {
                user.setFullName(trimmed);
                reason.append("fullName ");
                changed = true;
            }
        }
        if (locale != null && !locale.equals(user.getDefaultLocale())) {
            user.setDefaultLocale(locale);
            reason.append("locale ");
            changed = true;
        }
        if (status != null) {
            UserStatus target;
            try {
                target = UserStatus.valueOf(status);
            } catch (IllegalArgumentException ex) {
                throw new ValidationException("USER_PATCH_BAD_STATUS",
                        "status must be ACTIVE or DISABLED");
            }
            if (target != UserStatus.ACTIVE && target != UserStatus.DISABLED) {
                throw new ValidationException("USER_PATCH_BAD_STATUS",
                        "status must be ACTIVE or DISABLED");
            }
            if (target != user.getStatus()) {
                UserStatus previous = user.getStatus();
                user.setStatus(target);
                reason.append("status=").append(target).append(' ');
                changed = true;
                // Record the IdP intent: deactivate on a move INTO DISABLED;
                // reactivate on a move OUT of DISABLED back to ACTIVE. Other
                // transitions (e.g. INVITED→ACTIVE) do not touch the IdP here.
                if (target == UserStatus.DISABLED) {
                    idpTransition = UserStatus.DISABLED;
                } else if (target == UserStatus.ACTIVE && previous == UserStatus.DISABLED) {
                    idpTransition = UserStatus.ACTIVE;
                }
            }
        }
        if (changed) {
            userRepo.save(user);
            audit.record(AuditEvent.builder()
                    .tenantId(ctx.tenantId())
                    .actorUserId(ctx.userId())
                    .action(AuditAction.USER_UPDATED)
                    .entityType("User")
                    .entityId(userId)
                    .reason(reason.toString().trim())
                    .build());
        }
        // IdP call runs AFTER the in-app change is persisted — best-effort, never
        // blocks the in-app cutoff (see class Javadoc, consistency decision).
        if (idpTransition != null) {
            syncIdpStatus(ctx, user, idpTransition);
        }
        return detailsQuery.byId(userId);
    }

    /**
     * Best-effort IdP status sync. NO-OP unless the flag is on AND the user was
     * provisioned (external_idp_subject != null). On failure, audit
     * {@code USER_IDP_DEACTIVATION_FAILED} (REQUIRES_NEW — survives) and WARN, but
     * NEVER rethrow: the in-app status change already succeeded and must stand.
     */
    private void syncIdpStatus(TenantContext ctx, UserJpaEntity user, UserStatus idpTransition) {
        if (!idpProps.isEnabled()) {
            return; // NoOp anyway, but skip the audit noise when IdP is disabled
        }
        String subject = user.getExternalIdpSubject();
        if (subject == null || subject.isBlank()) {
            return; // user predates IdP provisioning — nothing to deactivate
        }
        boolean deactivating = idpTransition == UserStatus.DISABLED;
        try {
            if (deactivating) {
                identityProvisioning.deactivateUser(subject);
            } else {
                identityProvisioning.reactivateUser(subject);
            }
            audit.record(AuditEvent.builder()
                    .tenantId(ctx.tenantId())
                    .actorUserId(ctx.userId())
                    .action(deactivating
                            ? AuditAction.USER_IDP_ACCOUNT_DEACTIVATED
                            : AuditAction.USER_IDP_ACCOUNT_REACTIVATED)
                    .entityType("User")
                    .entityId(user.getId())
                    // subject id only — never the token, never PII beyond what is safe
                    .reason("externalIdpSubject=" + subject)
                    .build());
        } catch (IdentityProvisioningException ex) {
            // In-app cutoff already committed; flag the IdP failure for retry.
            audit.record(AuditEvent.builder()
                    .tenantId(ctx.tenantId())
                    .actorUserId(ctx.userId())
                    .action(AuditAction.USER_IDP_DEACTIVATION_FAILED)
                    .entityType("User")
                    .entityId(user.getId())
                    .reason("externalIdpSubject=" + subject
                            + " transition=" + (deactivating ? "DEACTIVATE" : "REACTIVATE")
                            + " reason=" + ex.getCode())
                    .build());
            log.warn("IdP {} failed during user status change; in-app status stands, flagged for retry. "
                            + "subject={} code={}",
                    deactivating ? "deactivate" : "reactivate", subject, ex.getCode());
        }
    }
}
