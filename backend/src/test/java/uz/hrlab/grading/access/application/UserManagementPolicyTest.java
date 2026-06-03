package uz.hrlab.grading.access.application;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.domain.RoleScope;
import uz.hrlab.grading.access.infrastructure.RoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link UserManagementPolicy} — the security-critical ABAC
 * gate of the user-management module (MVP 1 Phase 1.5).
 *
 * <p>Covers the four salary-permission invariants (HRLab role denial,
 * self-grant denial, cross-tenant denial, missing permission denial) plus
 * the cross-tenant view denial. All denials are EXPECTED — these tests
 * fail-closed and protect the platform from privilege-escalation regressions.
 */
@Tag("security")
class UserManagementPolicyTest {

    private static final UUID CALLER_ID  = UUID.randomUUID();
    private static final UUID TARGET_ID  = UUID.randomUUID();
    private static final UUID TENANT_ID  = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();

    private final UserTenantMembershipRepository repo = mock(UserTenantMembershipRepository.class);
    private final UserManagementPolicy policy = new UserManagementPolicy(repo);

    // ------------------------------------------------------------------------
    //  Salary toggle — four invariants
    // ------------------------------------------------------------------------

    @Test
    void salaryToggleDeniedWithoutPermission() {
        TenantContext ctx = ctx(Set.of(RoleCodes.CLIENT_COMPANY_ADMIN), Set.of());
        UserTenantMembershipJpaEntity target = membership(TARGET_ID, TENANT_ID);

        assertThatThrownBy(() ->
                policy.requireCanToggleSalaryPermission(ctx, target, List.of()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void salaryToggleDeniedForHrlabAttachedTarget() {
        // Caller is CLIENT_COMPANY_ADMIN with the toggle permission, but the
        // TARGET membership has an HRLab role attached → 403 (architecture
        // §8.4 separation of duties — HRLab consultants do not view client
        // salary, so they must not be granted the gate either).
        TenantContext ctx = ctx(Set.of(RoleCodes.CLIENT_COMPANY_ADMIN),
                Set.of(PermissionCodes.USER_SALARY_PERMISSION_TOGGLE));
        UserTenantMembershipJpaEntity target = membership(TARGET_ID, TENANT_ID);

        RoleJpaEntity hrlabRole = new RoleJpaEntity(UUID.randomUUID(),
                RoleCodes.HRLAB_CONSULTANT, "HRLab Consultant", RoleScope.PLATFORM);

        assertThatThrownBy(() ->
                policy.requireCanToggleSalaryPermission(ctx, target, List.of(hrlabRole)))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("HRLab");
    }

    @Test
    void salaryToggleDeniedOnSelfGrant() {
        // Caller flips their OWN row → forbidden (no privilege escalation
        // through self-action).
        TenantContext ctx = ctx(Set.of(RoleCodes.CLIENT_COMPANY_ADMIN),
                Set.of(PermissionCodes.USER_SALARY_PERMISSION_TOGGLE));
        UserTenantMembershipJpaEntity selfMembership = membership(CALLER_ID, TENANT_ID);

        assertThatThrownBy(() ->
                policy.requireCanToggleSalaryPermission(ctx, selfMembership, List.of()))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("Self");
    }

    @Test
    void salaryToggleDeniedAcrossTenants() {
        // Target membership belongs to a DIFFERENT tenant than the caller's
        // active context — must yield TenantAccessDenied (mapped to 404
        // upstream).
        TenantContext ctx = ctx(Set.of(RoleCodes.CLIENT_COMPANY_ADMIN),
                Set.of(PermissionCodes.USER_SALARY_PERMISSION_TOGGLE));
        UserTenantMembershipJpaEntity target = membership(TARGET_ID, OTHER_TENANT);

        assertThatThrownBy(() ->
                policy.requireCanToggleSalaryPermission(ctx, target, List.of()))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void salaryTogglePermitsHappyPath() {
        TenantContext ctx = ctx(Set.of(RoleCodes.CLIENT_COMPANY_ADMIN),
                Set.of(PermissionCodes.USER_SALARY_PERMISSION_TOGGLE));
        UserTenantMembershipJpaEntity target = membership(TARGET_ID, TENANT_ID);
        // Target only has a CLIENT role → no HRLab block.
        RoleJpaEntity clientRole = new RoleJpaEntity(UUID.randomUUID(),
                RoleCodes.CLIENT_HR_SPECIALIST, "HR Specialist", RoleScope.TENANT);

        assertThatCode(() ->
                policy.requireCanToggleSalaryPermission(ctx, target, List.of(clientRole)))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------------
    //  HRLab role assignment gate
    // ------------------------------------------------------------------------

    @Test
    void assigningHrlabRoleRequiresHrlabAssignPermission() {
        TenantContext ctx = ctx(Set.of(RoleCodes.CLIENT_COMPANY_ADMIN),
                Set.of(PermissionCodes.USER_ROLE_ASSIGN));
        RoleJpaEntity hrlab = new RoleJpaEntity(UUID.randomUUID(),
                RoleCodes.HRLAB_PROJECT_MANAGER, "HRLab PM", RoleScope.PLATFORM);

        assertThatThrownBy(() -> policy.requireCanAssignRole(ctx, hrlab))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void assigningClientRoleRequiresNoExtraPermission() {
        TenantContext ctx = ctx(Set.of(RoleCodes.CLIENT_COMPANY_ADMIN),
                Set.of(PermissionCodes.USER_ROLE_ASSIGN));
        RoleJpaEntity client = new RoleJpaEntity(UUID.randomUUID(),
                RoleCodes.CLIENT_HR_DIRECTOR, "HR Director", RoleScope.TENANT);

        assertThatCode(() -> policy.requireCanAssignRole(ctx, client))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------------
    //  Cross-tenant view denial
    // ------------------------------------------------------------------------

    @Test
    void viewDeniedWhenNoOverlap() {
        TenantContext ctx = ctxWithMemberships(Set.of(RoleCodes.CLIENT_COMPANY_ADMIN),
                Set.of(), Set.of(TENANT_ID));
        // Target only belongs to OTHER_TENANT → no overlap with caller.
        List<UserTenantMembershipJpaEntity> targetMemberships =
                List.of(membership(TARGET_ID, OTHER_TENANT));

        assertThatThrownBy(() -> policy.requireCanViewUser(ctx, targetMemberships))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void viewPermittedWhenOverlapExists() {
        TenantContext ctx = ctxWithMemberships(Set.of(RoleCodes.CLIENT_COMPANY_ADMIN),
                Set.of(), Set.of(TENANT_ID));
        List<UserTenantMembershipJpaEntity> targetMemberships = List.of(
                membership(TARGET_ID, OTHER_TENANT),
                membership(TARGET_ID, TENANT_ID));

        assertThatCode(() -> policy.requireCanViewUser(ctx, targetMemberships))
                .doesNotThrowAnyException();
    }

    @Test
    void superAdminBypassesOverlapCheck() {
        TenantContext ctx = ctxWithMemberships(Set.of(RoleCodes.HRLAB_SUPER_ADMIN),
                Set.of(), Set.of(TENANT_ID));
        // Target lives entirely in OTHER_TENANT — Super Admin still sees them.
        List<UserTenantMembershipJpaEntity> targetMemberships =
                List.of(membership(TARGET_ID, OTHER_TENANT));

        assertThatCode(() -> policy.requireCanViewUser(ctx, targetMemberships))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------------

    private static TenantContext ctx(Set<String> roles, Set<String> permissions) {
        return new TenantContext(CALLER_ID, TENANT_ID, Set.of(), roles, permissions,
                Set.of(), false, "ru-RU", Set.of(TENANT_ID));
    }

    private static TenantContext ctxWithMemberships(Set<String> roles, Set<String> permissions,
                                                    Set<UUID> tenantMemberships) {
        return new TenantContext(CALLER_ID, TENANT_ID, Set.of(), roles, permissions,
                Set.of(), false, "ru-RU", tenantMemberships);
    }

    private static UserTenantMembershipJpaEntity membership(UUID userId, UUID tenantId) {
        return new UserTenantMembershipJpaEntity(UUID.randomUUID(), userId, tenantId,
                MembershipStatus.ACTIVE, false);
    }
}
