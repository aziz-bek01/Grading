package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.api.UserDetailsResponse;
import uz.hrlab.grading.access.api.UserMembershipSummary;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.infrastructure.RoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
import uz.hrlab.grading.access.infrastructure.UserJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRepository;
import uz.hrlab.grading.access.infrastructure.UserRoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRoleRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;
import uz.hrlab.grading.tenancy.infrastructure.TenantJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Query handler for {@code GET /api/v1/users/{id}}.
 *
 * <p>Loads the target user with EVERY membership for ABAC overlap evaluation,
 * but the response is SCOPED — non-super-admin callers see only the
 * memberships whose {@code tenantId} they themselves belong to. This prevents
 * a CLIENT_COMPANY_ADMIN of Tenant A from learning that the same person is
 * also a member of Tenant B (membership disclosure attack).
 */
@Service
public class GetUserDetailsQuery {

    private final UserManagementPolicy policy;
    private final UserRepository userRepo;
    private final UserTenantMembershipRepository membershipRepo;
    private final UserRoleRepository userRoleRepo;
    private final RoleRepository roleRepo;
    private final TenantRepository tenantRepo;

    public GetUserDetailsQuery(UserManagementPolicy policy,
                               UserRepository userRepo,
                               UserTenantMembershipRepository membershipRepo,
                               UserRoleRepository userRoleRepo,
                               RoleRepository roleRepo,
                               TenantRepository tenantRepo) {
        this.policy = policy;
        this.userRepo = userRepo;
        this.membershipRepo = membershipRepo;
        this.userRoleRepo = userRoleRepo;
        this.roleRepo = roleRepo;
        this.tenantRepo = tenantRepo;
    }

    @Transactional(readOnly = true)
    public UserDetailsResponse byId(UUID userId) {
        TenantContext ctx = TenantContextHolder.requireActive();

        UserJpaEntity user = userRepo.findById(userId)
                .orElseThrow(TenantAccessDeniedException::new);
        List<UserTenantMembershipJpaEntity> allMemberships =
                membershipRepo.findAllByUserId(userId);

        // ABAC overlap check first — denial yields 404 to defeat probing.
        policy.requireCanViewUser(ctx, allMemberships);

        // Scope memberships in the RESPONSE: super-admin sees everything,
        // every other caller sees only memberships in their own tenant set.
        List<UserTenantMembershipJpaEntity> visible = scopeForCaller(ctx, allMemberships);

        return new UserDetailsResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus() == null ? null : user.getStatus().name(),
                user.getDefaultLocale(),
                user.getLastLoginAt(),
                buildMembershipCards(visible)
        );
    }

    private List<UserTenantMembershipJpaEntity> scopeForCaller(
            TenantContext ctx, List<UserTenantMembershipJpaEntity> all) {
        if (ctx.hasRole(RoleCodes.HRLAB_SUPER_ADMIN)) return all;
        Set<UUID> callerTenants = ctx.tenantMemberships();
        if (callerTenants == null || callerTenants.isEmpty()) return List.of();
        return all.stream()
                .filter(m -> callerTenants.contains(m.getTenantId()))
                .filter(m -> m.getStatus() != MembershipStatus.REVOKED)
                .toList();
    }

    private List<UserMembershipSummary> buildMembershipCards(
            List<UserTenantMembershipJpaEntity> memberships) {
        if (memberships.isEmpty()) return List.of();

        Set<UUID> tenantIds = memberships.stream()
                .map(UserTenantMembershipJpaEntity::getTenantId)
                .collect(Collectors.toSet());
        Map<UUID, TenantJpaEntity> tenantsById = tenantRepo.findAllById(tenantIds).stream()
                .collect(Collectors.toMap(TenantJpaEntity::getId, t -> t));

        Map<UUID, List<UserRoleJpaEntity>> rolesByMembership = memberships.stream()
                .collect(Collectors.toMap(
                        UserTenantMembershipJpaEntity::getId,
                        m -> userRoleRepo.findAllByMembershipId(m.getId())));

        Set<UUID> roleIds = rolesByMembership.values().stream()
                .flatMap(List::stream)
                .map(UserRoleJpaEntity::getRoleId)
                .collect(Collectors.toSet());
        Map<UUID, RoleJpaEntity> rolesById = roleIds.isEmpty()
                ? Map.of()
                : roleRepo.findAllById(roleIds).stream()
                    .collect(Collectors.toMap(RoleJpaEntity::getId, r -> r));

        List<UserMembershipSummary> out = new ArrayList<>(memberships.size());
        for (UserTenantMembershipJpaEntity m : memberships) {
            TenantJpaEntity tenant = tenantsById.get(m.getTenantId());
            List<UserMembershipSummary.RoleSummary> roleCards = rolesByMembership.get(m.getId())
                    .stream()
                    .map(ur -> {
                        RoleJpaEntity r = rolesById.get(ur.getRoleId());
                        if (r == null) return null;
                        return new UserMembershipSummary.RoleSummary(
                                ur.getId(), r.getId(), r.getCode(), r.getName(),
                                r.getScope() == null ? null : r.getScope().name(),
                                UserManagementPolicy.isHrlabRole(r.getCode()));
                    })
                    .filter(rs -> rs != null)
                    .toList();
            out.add(new UserMembershipSummary(
                    m.getId(),
                    m.getTenantId(),
                    tenant == null ? null : tenant.getSlug(),
                    tenant == null ? null : tenant.getDisplayName(),
                    m.getStatus() == null ? null : m.getStatus().name(),
                    m.isSalaryDataPermission(),
                    m.getCreatedAt(),
                    roleCards));
        }
        return out;
    }
}
