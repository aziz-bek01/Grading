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
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

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
 *
 * <h3>Credential edits (decision doc 08 extension)</h3>
 * <ul>
 *   <li><b>password</b> — admin-set IdP credential reset. Requires the IdP
 *       feature flag ON and an existing {@code external_idp_subject}; a user with
 *       no IdP account cannot have a password (ValidationException
 *       {@code USER_NO_IDP_ACCOUNT}). The password is validated against the same
 *       {@link PasswordPolicy} as invite, pushed via {@code setPassword}, and
 *       audited as {@code USER_PASSWORD_RESET} — NEVER carrying the value.
 *       <b>Fail-fast (decouple decision):</b> the IdP push runs BEFORE any DB
 *       write, so a password failure leaves NOTHING half-saved. When ZITADEL
 *       returns an actionable 4xx (e.g. the legacy account is not yet
 *       initialised), {@code setPassword} throws an
 *       {@link IdentityProvisioningException} carrying a SPECIFIC reason that the
 *       API surfaces as a clear {@code 400} ({@code USER_IDP_NOT_INITIALIZED} /
 *       {@code USER_IDP_PASSWORD_REJECTED}) instead of a confusing {@code 502}.
 *       The admin clears the password field and re-saves; a blank password never
 *       calls the IdP, so profile-only edits always save. A genuine upstream
 *       failure still surfaces as {@code 502 IDP_PROVISIONING_FAILED}.</li>
 *   <li><b>email</b> — the principal RESOLVE KEY, so grading and the IdP MUST
 *       stay in sync. Consistency model here is the OPPOSITE of status:
 *       ALL-OR-NOTHING. Inside the {@code @Transactional}, {@code users.email} is
 *       updated and then (if IdP enabled and provisioned) {@code changeEmail} is
 *       called; if ZITADEL rejects it, the exception PROPAGATES and the tx ROLLS
 *       BACK the grading email — both stores stay on the OLD value (consistent).
 *       On success both are NEW (consistent). With the IdP disabled the grading
 *       email is updated alone. Uniqueness is enforced against
 *       {@code findByEmailIgnoreCase} ({@code USER_EMAIL_TAKEN}).</li>
 * </ul>
 */
@Service
public class PatchUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(PatchUserUseCase.class);

    /**
     * Lightweight syntactic email guard, mirroring the {@code @Email} bound on
     * {@link uz.hrlab.grading.access.api.PatchUserRequest}. The bean rule covers
     * the HTTP path; this re-check guards programmatic callers and keeps the rule
     * close to the uniqueness check.
     */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

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
    public UserDetailsResponse patch(UUID userId, String fullName, String locale, String status,
                                     String email, String password) {
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
        // The previous email, captured before any mutation, for the
        // USER_EMAIL_CHANGED audit (old→new). null = email not changing.
        String emailChangedFrom = null;

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
        if (email != null) {
            String trimmed = email.trim();
            if (trimmed.isEmpty()) {
                throw new ValidationException("USER_PATCH_BLANK_EMAIL",
                        "email cannot be blank when provided");
            }
            // Case-insensitive: only act when it genuinely differs.
            if (!trimmed.equalsIgnoreCase(user.getEmail())) {
                if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
                    throw new ValidationException("USER_PATCH_BAD_EMAIL",
                            "email is not a valid address");
                }
                // Uniqueness: the principal resolve key must stay 1:1 with a user.
                userRepo.findByEmailIgnoreCase(trimmed).ifPresent(other -> {
                    if (!other.getId().equals(user.getId())) {
                        throw new ValidationException("USER_EMAIL_TAKEN",
                                "email is already in use by another user");
                    }
                });
                emailChangedFrom = user.getEmail();
                // Store lower-cased to match the invite flow + the unique index.
                user.setEmail(trimmed.toLowerCase(Locale.ROOT));
                reason.append("email ");
                changed = true;
            }
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
        // Password reset is independent of the grading row (the credential lives
        // ONLY in the IdP). Validate + require an IdP account BEFORE any write so a
        // bad request never half-applies. Push happens below, inside the same tx.
        boolean resetPassword = password != null && !password.isBlank();
        if (resetPassword) {
            PasswordPolicy.validate(password, "USER_PASSWORD_WEAK");
            if (!idpProps.isEnabled() || user.getExternalIdpSubject() == null
                    || user.getExternalIdpSubject().isBlank()) {
                // No credential store to write to — surface a clear, actionable error
                // rather than silently dropping the password.
                throw new ValidationException("USER_NO_IDP_ACCOUNT",
                        "This user has no identity-provider account, so a password cannot be set.");
            }
        }

        // PASSWORD — attempt the IdP push FIRST, before any DB write, so a
        // password failure leaves NOTHING half-saved (fail-fast). This is the
        // decouple decision: rather than a partial-success envelope, we fail the
        // whole PATCH cleanly with a SPECIFIC, actionable code when the password
        // is the problem (e.g. the legacy account is not yet initialised → 400
        // USER_IDP_NOT_INITIALIZED). The admin can then clear the password field
        // and re-save the profile, which always succeeds (blank password never
        // calls the IdP — see the resetPassword guard above). NEVER audit/log the
        // value. If the IdP rejects, the exception propagates and the
        // @Transactional rolls back (no row was written yet anyway).
        if (resetPassword) {
            identityProvisioning.setPassword(user.getExternalIdpSubject(), password);
            audit.record(AuditEvent.builder(ctx)
                    .action(AuditAction.USER_PASSWORD_RESET)
                    .entityType("User")
                    .entityId(userId)
                    .reason("externalIdpSubject=" + user.getExternalIdpSubject())
                    .build());
        }

        if (changed) {
            userRepo.save(user);
            audit.record(AuditEvent.builder(ctx)
                    .action(AuditAction.USER_UPDATED)
                    .entityType("User")
                    .entityId(userId)
                    .reason(reason.toString().trim())
                    .build());
        }

        // EMAIL — ALL-OR-NOTHING (opposite of status). The grading email is
        // already set above; if the IdP push fails the exception PROPAGATES so the
        // @Transactional rolls the grading email back. Both stores stay consistent
        // (old/old on failure, new/new on success). IdP disabled ⇒ grading only.
        if (emailChangedFrom != null) {
            String subject = user.getExternalIdpSubject();
            if (idpProps.isEnabled() && subject != null && !subject.isBlank()) {
                identityProvisioning.changeEmail(subject, user.getEmail());
            }
            // old→new are not secrets — safe to audit for forensics.
            audit.record(AuditEvent.builder(ctx)
                    .action(AuditAction.USER_EMAIL_CHANGED)
                    .entityType("User")
                    .entityId(userId)
                    .reason("from=" + emailChangedFrom + " to=" + user.getEmail())
                    .build());
        }

        // IdP STATUS call runs AFTER the in-app change is persisted — best-effort,
        // never blocks the in-app cutoff (see class Javadoc, consistency decision).
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
            audit.record(AuditEvent.builder(ctx)
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
            audit.record(AuditEvent.builder(ctx)
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
