package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.api.UserDetailsResponse;
import uz.hrlab.grading.access.infrastructure.RoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
import uz.hrlab.grading.access.infrastructure.UserRoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRoleRepository;
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
import java.util.Map;
import java.util.UUID;

/**
 * Use case for {@code PATCH
 * /api/v1/users/{id}/memberships/{tenantId}/salary-permission}.
 *
 * <p>The strictest endpoint in the user-management module — architecture §8.4
 * mandates a separate gate for the salary axis. Four checks:
 * <ol>
 *   <li>caller holds {@link PermissionCodes#USER_SALARY_PERMISSION_TOGGLE};</li>
 *   <li>target membership belongs to the caller's active tenant;</li>
 *   <li>self-grant blocked (no privilege escalation through self-action);</li>
 *   <li>target user does NOT hold any HRLab platform role in this tenant.</li>
 * </ol>
 *
 * <p>Mandatory non-blank {@code reason} lands in the audit log
 * {@code reason} column — auditors require a one-line rationale for every
 * salary-permission flip.
 */
@Service
public class ToggleSalaryPermissionUseCase {

    private final UserManagementPolicy policy;
    private final UserTenantMembershipRepository membershipRepo;
    private final UserRoleRepository userRoleRepo;
    private final RoleRepository roleRepo;
    private final GetUserDetailsQuery detailsQuery;
    private final AuditService audit;

    public ToggleSalaryPermissionUseCase(UserManagementPolicy policy,
                                         UserTenantMembershipRepository membershipRepo,
                                         UserRoleRepository userRoleRepo,
                                         RoleRepository roleRepo,
                                         GetUserDetailsQuery detailsQuery,
                                         AuditService audit) {
        this.policy = policy;
        this.membershipRepo = membershipRepo;
        this.userRoleRepo = userRoleRepo;
        this.roleRepo = roleRepo;
        this.detailsQuery = detailsQuery;
        this.audit = audit;
    }

    @Transactional
    public UserDetailsResponse toggle(UUID userId, UUID tenantId, boolean enabled, String reason) {
        TenantContext ctx = TenantContextHolder.requireActive();

        if (reason == null || reason.isBlank()) {
            throw new ValidationException("USER_SALARY_REASON_REQUIRED",
                    "A non-blank reason is required for salary-permission changes");
        }

        UserTenantMembershipJpaEntity membership = membershipRepo
                .findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(TenantAccessDeniedException::new);

        List<UserRoleJpaEntity> userRoles = userRoleRepo.findAllByMembershipId(membership.getId());
        Map<UUID, RoleJpaEntity> rolesById = userRoles.isEmpty()
                ? Map.of()
                : roleRepo.findAllById(userRoles.stream().map(UserRoleJpaEntity::getRoleId).toList()).stream()
                    .collect(java.util.stream.Collectors.toMap(RoleJpaEntity::getId, r -> r));
        List<RoleJpaEntity> attachedRoles = userRoles.stream()
                .map(ur -> rolesById.get(ur.getRoleId()))
                .filter(r -> r != null)
                .toList();

        policy.requireCanToggleSalaryPermission(ctx, membership, attachedRoles);

        if (membership.isSalaryDataPermission() == enabled) {
            // Idempotent — still emit the audit row so the rationale is
            // captured even when the actual flag value did not change.
        }
        membership.setSalaryDataPermission(enabled);
        membershipRepo.save(membership);

        String action = enabled
                ? AuditAction.USER_SALARY_PERMISSION_GRANTED
                : AuditAction.USER_SALARY_PERMISSION_REVOKED;
        String truncatedReason = reason.length() > 1000 ? reason.substring(0, 1000) : reason;
        audit.record(AuditEvent.builder()
                .tenantId(tenantId)
                .actorUserId(ctx.userId())
                .action(action)
                .entityType("UserTenantMembership")
                .entityId(membership.getId())
                .reason("userId=" + userId + " enabled=" + enabled + " reason=" + truncatedReason)
                .build());

        return detailsQuery.byId(userId);
    }
}
