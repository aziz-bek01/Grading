package uz.hrlab.grading.access.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;

import java.util.UUID;

/**
 * Blast-radius control for the ONE cross-tenant role,
 * {@link RoleCodes#HRLAB_SUPER_ADMIN} (Task #6). A platform super admin can act
 * in EVERY active tenant (see {@link PlatformSuperAdminChecker} + the Fix A
 * super-admin tenant switcher), so granting the role is the highest-blast-radius
 * action in the system and must be conspicuously audited + alertable.
 *
 * <h3>Single choke point (DRY)</h3>
 * This component is the ONE place that turns an {@code HRLAB_SUPER_ADMIN}
 * user_role transition into its dedicated {@link AuditAction#PLATFORM_SUPER_ADMIN_GRANTED}
 * / {@link AuditAction#PLATFORM_SUPER_ADMIN_REVOKED} audit event + alert log. It
 * is reused by every code path that materialises or deletes such a row —
 * {@code AssignRoleUseCase}, {@code AddMembershipUseCase}, {@code InviteUserUseCase},
 * {@code CreateTenantUseCase}. Callers pass the role code UNCONDITIONALLY; the
 * short-circuit here makes it a NO-OP for every other role, so a normal role
 * change is never double-audited and the existing {@code USER_ROLE_ASSIGNED} /
 * {@code USER_ROLE_REMOVED} auditing of those paths is left untouched. The
 * dedicated event is ADDITIVE framing on top of that generic row (mirrors the
 * {@code METHODOLOGY_APPROVED_EDIT} umbrella pattern).
 *
 * <h3>Alert (no email/pager infra)</h3>
 * A grant/revoke also emits a WARN-level, greppable structured log carrying the
 * actor + target user ids ONLY (never PII or secrets) so an observability rule
 * (Loki/Grafana on the {@code marker=SUPER_ADMIN_GRANT} field) can fire an alert.
 * See {@code docs/ops/runbook.md} §3.9.
 *
 * <p>Stateless and thread-safe.
 */
@Component
public class SuperAdminRoleAuditor {

    /**
     * Greppable log markers for the observability/alert rule. Do NOT change
     * without updating the alert query in {@code docs/ops/runbook.md} §3.9.
     */
    public static final String GRANT_LOG_MARKER  = "SUPER_ADMIN_GRANT";
    public static final String REVOKE_LOG_MARKER = "SUPER_ADMIN_REVOKE";

    private static final Logger log = LoggerFactory.getLogger(SuperAdminRoleAuditor.class);

    private final AuditService audit;

    public SuperAdminRoleAuditor(AuditService audit) {
        this.audit = audit;
    }

    /**
     * Record + alert when {@code HRLAB_SUPER_ADMIN} was just granted to a user.
     * NO-OP for any other {@code roleCode}.
     *
     * @param actorUserId  who performed the grant (the authenticated caller)
     * @param tenantId     the membership tenant the role row was written in
     * @param targetUserId who received the role
     * @param roleCode     the role code being granted (checked here)
     * @param userRoleId   the persisted {@code user_roles} row id (forensics)
     * @param context      short code-path tag (e.g. {@code assign-role})
     */
    public void onRoleGranted(UUID actorUserId, UUID tenantId, UUID targetUserId,
                              String roleCode, UUID userRoleId, String context) {
        if (!RoleCodes.HRLAB_SUPER_ADMIN.equals(roleCode)) {
            return;
        }
        audit.record(AuditEvent.builder()
                .tenantId(tenantId)
                .actorUserId(actorUserId)
                .action(AuditAction.PLATFORM_SUPER_ADMIN_GRANTED)
                .entityType("User")
                .entityId(targetUserId)
                .reason("targetUserId=" + targetUserId + " userRoleId=" + userRoleId
                        + " context=" + context)
                .build());
        // WARN so an ops alert rule can trigger — ids ONLY, no PII/secrets.
        log.warn("marker={} PLATFORM_SUPER_ADMIN granted actorUserId={} targetUserId={} "
                        + "tenantId={} context={}",
                GRANT_LOG_MARKER, actorUserId, targetUserId, tenantId, context);
    }

    /**
     * Record + alert when {@code HRLAB_SUPER_ADMIN} was just revoked from a user.
     * NO-OP for any other {@code roleCode} (a {@code null} code is treated as
     * "not super admin" and is a NO-OP).
     */
    public void onRoleRevoked(UUID actorUserId, UUID tenantId, UUID targetUserId,
                              String roleCode, UUID userRoleId, String context) {
        if (!RoleCodes.HRLAB_SUPER_ADMIN.equals(roleCode)) {
            return;
        }
        audit.record(AuditEvent.builder()
                .tenantId(tenantId)
                .actorUserId(actorUserId)
                .action(AuditAction.PLATFORM_SUPER_ADMIN_REVOKED)
                .entityType("User")
                .entityId(targetUserId)
                .reason("targetUserId=" + targetUserId + " userRoleId=" + userRoleId
                        + " context=" + context)
                .build());
        log.warn("marker={} PLATFORM_SUPER_ADMIN revoked actorUserId={} targetUserId={} "
                        + "tenantId={} context={}",
                REVOKE_LOG_MARKER, actorUserId, targetUserId, tenantId, context);
    }
}
