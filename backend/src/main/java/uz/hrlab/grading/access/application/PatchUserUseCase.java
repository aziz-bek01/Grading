package uz.hrlab.grading.access.application;

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
 */
@Service
public class PatchUserUseCase {

    private final UserManagementPolicy policy;
    private final UserRepository userRepo;
    private final UserTenantMembershipRepository membershipRepo;
    private final GetUserDetailsQuery detailsQuery;
    private final AuditService audit;

    public PatchUserUseCase(UserManagementPolicy policy,
                            UserRepository userRepo,
                            UserTenantMembershipRepository membershipRepo,
                            GetUserDetailsQuery detailsQuery,
                            AuditService audit) {
        this.policy = policy;
        this.userRepo = userRepo;
        this.membershipRepo = membershipRepo;
        this.detailsQuery = detailsQuery;
        this.audit = audit;
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
                user.setStatus(target);
                reason.append("status=").append(target).append(' ');
                changed = true;
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
        return detailsQuery.byId(userId);
    }
}
