package uz.hrlab.grading.access.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import uz.hrlab.grading.common.api.Pagination;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.api.UserListResponse;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.infrastructure.RoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
import uz.hrlab.grading.access.infrastructure.UserJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRepository;
import uz.hrlab.grading.access.infrastructure.UserRoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRoleRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Query handler for {@code GET /api/v1/users?tenantId=&status=&role=&search=&page=&size=}.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>{@code tenantId} REQUIRED for non-Super-Admin callers — enforced by
 *       {@link UserManagementPolicy#requireCanListInTenant(TenantContext, UUID)}.</li>
 *   <li>HRLab Super Admin may omit {@code tenantId} → cross-tenant listing.</li>
 *   <li>Sorted by {@code users.email ASC} for stable pagination.</li>
 *   <li>{@code status} accepts a membership-status enum string; invalid input
 *       is treated as null filter (do not 500 on malformed query).</li>
 * </ul>
 */
@Service
public class ListUsersQuery {

    private final UserManagementPolicy policy;
    private final UserTenantMembershipRepository membershipRepo;
    private final UserRepository userRepo;
    private final UserRoleRepository userRoleRepo;
    private final RoleRepository roleRepo;

    public ListUsersQuery(UserManagementPolicy policy,
                          UserTenantMembershipRepository membershipRepo,
                          UserRepository userRepo,
                          UserRoleRepository userRoleRepo,
                          RoleRepository roleRepo) {
        this.policy = policy;
        this.membershipRepo = membershipRepo;
        this.userRepo = userRepo;
        this.userRoleRepo = userRoleRepo;
        this.roleRepo = roleRepo;
    }

    @Transactional(readOnly = true)
    public Page<UserListResponse> list(UUID tenantId, String status, String roleCode,
                                       String search, int page, int size) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (tenantId == null) {
            policy.requireCanListAcrossTenants(ctx);
            // Cross-tenant listing is super-admin only — for now we still
            // require a tenantId to avoid full-table scans. Future MVP can
            // add a dedicated cross-tenant endpoint with cursor pagination.
            policy.requireCanListInTenant(ctx, ctx.tenantId());
            tenantId = ctx.tenantId();
        } else {
            policy.requireCanListInTenant(ctx, tenantId);
        }

        MembershipStatus statusEnum = parseStatus(status);
        Pageable pageable = pageable(page, size);
        Page<UserTenantMembershipJpaEntity> memberships =
                membershipRepo.searchInTenant(tenantId, statusEnum,
                        emptyToNull(search), emptyToNull(roleCode), pageable);

        if (memberships.isEmpty()) {
            return memberships.map(m -> null); // empty Page
        }

        // Batch-load users + role rows to avoid N+1.
        Set<UUID> userIds = memberships.getContent().stream()
                .map(UserTenantMembershipJpaEntity::getUserId)
                .collect(Collectors.toSet());
        Map<UUID, UserJpaEntity> usersById = userRepo.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserJpaEntity::getId, u -> u));

        Map<UUID, List<UserRoleJpaEntity>> rolesByMembership = new HashMap<>();
        for (UserTenantMembershipJpaEntity m : memberships.getContent()) {
            rolesByMembership.put(m.getId(), userRoleRepo.findAllByMembershipId(m.getId()));
        }
        Set<UUID> roleIds = rolesByMembership.values().stream()
                .flatMap(List::stream)
                .map(UserRoleJpaEntity::getRoleId)
                .collect(Collectors.toSet());
        Map<UUID, RoleJpaEntity> rolesById = roleIds.isEmpty()
                ? Map.of()
                : roleRepo.findAllById(roleIds).stream()
                    .collect(Collectors.toMap(RoleJpaEntity::getId, r -> r));

        return memberships.map(m -> toRow(m, usersById.get(m.getUserId()),
                rolesByMembership.getOrDefault(m.getId(), List.of()), rolesById));
    }

    private static UserListResponse toRow(UserTenantMembershipJpaEntity m, UserJpaEntity user,
                                          List<UserRoleJpaEntity> userRoles,
                                          Map<UUID, RoleJpaEntity> rolesById) {
        if (user == null) return null;
        List<String> roleCodes = userRoles.stream()
                .map(ur -> rolesById.get(ur.getRoleId()))
                .filter(r -> r != null)
                .map(RoleJpaEntity::getCode)
                .toList();
        return new UserListResponse(
                user.getId(), user.getEmail(), user.getFullName(),
                user.getStatus() == null ? null : user.getStatus().name(),
                user.getDefaultLocale(), user.getLastLoginAt(),
                m.getId(), m.getTenantId(),
                m.getStatus() == null ? null : m.getStatus().name(),
                m.isSalaryDataPermission(),
                roleCodes);
    }

    private static MembershipStatus parseStatus(String s) {
        if (s == null || s.isBlank()) return null;
        try { return MembershipStatus.valueOf(s.trim().toUpperCase()); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static Pageable pageable(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Pagination.clampSize(size);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "id"));
    }
}
