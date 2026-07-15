package uz.hrlab.grading.tenancy.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.access.application.SuperAdminRoleAuditor;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.domain.RoleScope;
import uz.hrlab.grading.access.infrastructure.RoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
import uz.hrlab.grading.access.infrastructure.UserRoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRoleRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.tenancy.domain.IsolationMode;
import uz.hrlab.grading.tenancy.domain.Tenant;
import uz.hrlab.grading.tenancy.domain.TenantStatus;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyRepository;
import uz.hrlab.grading.tenancy.infrastructure.TenancyProperties;
import uz.hrlab.grading.tenancy.infrastructure.TenantJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CreateTenantUseCase} — focuses on the shared-mode
 * immediate-usability behaviour (BE-TI-2026-06):
 * <ol>
 *   <li>shared mode creates the tenant ACTIVE and auto-members the creator with
 *       their HRLab platform role;</li>
 *   <li>only PLATFORM-scoped roles are mirrored;</li>
 *   <li>re-running is idempotent (no duplicate membership/role rows);</li>
 *   <li>non-shared (SCHEMA/DATABASE) mode keeps the PROVISIONING path with no
 *       auto-membership.</li>
 * </ol>
 */
@Tag("security")
class CreateTenantUseCaseTest {

    private static final UUID CREATOR_ID = UUID.randomUUID();
    private static final UUID SUPER_ADMIN_ROLE_ID = UUID.randomUUID();

    private final TenantRepository tenantRepo = mock(TenantRepository.class);
    private final ClientCompanyRepository companyRepo = mock(ClientCompanyRepository.class);
    private final UserTenantMembershipRepository membershipRepo =
            mock(UserTenantMembershipRepository.class);
    private final UserRoleRepository userRoleRepo = mock(UserRoleRepository.class);
    private final RoleRepository roleRepo = mock(RoleRepository.class);
    private final AuditService audit = mock(AuditService.class);
    // Real auditor over the mocked AuditService so the self-mirror super-admin
    // grant path is exercised end-to-end (records via the same mock).
    private final SuperAdminRoleAuditor superAdminAuditor = new SuperAdminRoleAuditor(audit);

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private CreateTenantUseCase useCase(TenancyProperties.Mode mode) {
        TenancyProperties props = new TenancyProperties();
        props.setMode(mode);
        return new CreateTenantUseCase(tenantRepo, companyRepo, membershipRepo,
                userRoleRepo, roleRepo, props, audit, superAdminAuditor);
    }

    private static CreateTenantCommand command() {
        return new CreateTenantCommand("acme", "ACME Bank", "ru-RU",
                IsolationMode.SCHEMA, "ACME LLC", "ACME", "Banking");
    }

    private void givenSuperAdminCreator() {
        TenantContextHolder.set(new TenantContext(CREATOR_ID, UUID.randomUUID(), Set.of(),
                Set.of(RoleCodes.HRLAB_SUPER_ADMIN), Set.of(), Set.of(), false, "ru-RU"));
        when(roleRepo.findByCode(RoleCodes.HRLAB_SUPER_ADMIN)).thenReturn(
                Optional.of(new RoleJpaEntity(SUPER_ADMIN_ROLE_ID,
                        RoleCodes.HRLAB_SUPER_ADMIN, "HRLab Super Admin", RoleScope.PLATFORM)));
    }

    @Test
    void sharedModeCreatesActiveTenantAndAutoMembersCreator() {
        when(tenantRepo.existsBySlug("acme")).thenReturn(false);
        givenSuperAdminCreator();
        when(membershipRepo.findByUserIdAndTenantId(eq(CREATOR_ID), any())).thenReturn(Optional.empty());
        when(userRoleRepo.existsByMembershipIdAndRoleId(any(), eq(SUPER_ADMIN_ROLE_ID)))
                .thenReturn(false);

        Tenant created = useCase(TenancyProperties.Mode.SHARED).create(command());

        // (1) Tenant is created ACTIVE (immediately usable, no saga).
        assertThat(created.status()).isEqualTo(TenantStatus.ACTIVE);
        verify(tenantRepo).save(any(TenantJpaEntity.class));

        // (2) Creator is auto-membered ACTIVE with salary_data_permission = false.
        verify(membershipRepo).save(org.mockito.ArgumentMatchers.argThat(m ->
                m.getUserId().equals(CREATOR_ID)
                        && m.getStatus() == MembershipStatus.ACTIVE
                        && !m.isSalaryDataPermission()));

        // (3) Creator gets their HRLab platform role mirrored into the new tenant.
        verify(userRoleRepo).save(org.mockito.ArgumentMatchers.argThat(ur ->
                ur.getRoleId().equals(SUPER_ADMIN_ROLE_ID)));

        // (4) Blast-radius control: the self-mirror of HRLAB_SUPER_ADMIN emits the
        // distinct PLATFORM_SUPER_ADMIN_GRANTED audit action (Task #6) on top of
        // the generic USER_ROLE_ASSIGNED row.
        verify(audit).record(org.mockito.ArgumentMatchers.argThat(e ->
                uz.hrlab.grading.audit.application.AuditAction.PLATFORM_SUPER_ADMIN_GRANTED
                        .equals(e.action())
                        && CREATOR_ID.equals(e.actorUserId())
                        && CREATOR_ID.equals(e.entityId())));
    }

    @Test
    void sharedModeIsIdempotentWhenCreatorAlreadyMembered() {
        when(tenantRepo.existsBySlug("acme")).thenReturn(false);
        givenSuperAdminCreator();
        UUID membershipId = UUID.randomUUID();
        UserTenantMembershipJpaEntity existing = new UserTenantMembershipJpaEntity(
                membershipId, CREATOR_ID, UUID.randomUUID(), MembershipStatus.ACTIVE, false);
        when(membershipRepo.findByUserIdAndTenantId(eq(CREATOR_ID), any()))
                .thenReturn(Optional.of(existing));
        when(userRoleRepo.existsByMembershipIdAndRoleId(eq(membershipId), eq(SUPER_ADMIN_ROLE_ID)))
                .thenReturn(true);

        useCase(TenancyProperties.Mode.SHARED).create(command());

        // No duplicate membership row and no duplicate role row.
        verify(membershipRepo, never()).save(any(UserTenantMembershipJpaEntity.class));
        verify(userRoleRepo, never()).save(any(UserRoleJpaEntity.class));
    }

    @Test
    void onlyPlatformRolesAreMirrored() {
        when(tenantRepo.existsBySlug("acme")).thenReturn(false);
        // Creator holds a TENANT-scoped role only — nothing should be mirrored.
        TenantContextHolder.set(new TenantContext(CREATOR_ID, UUID.randomUUID(), Set.of(),
                Set.of(RoleCodes.CLIENT_COMPANY_ADMIN), Set.of(), Set.of(), false, "ru-RU"));
        when(roleRepo.findByCode(RoleCodes.CLIENT_COMPANY_ADMIN)).thenReturn(
                Optional.of(new RoleJpaEntity(UUID.randomUUID(),
                        RoleCodes.CLIENT_COMPANY_ADMIN, "Client Admin", RoleScope.TENANT)));
        when(membershipRepo.findByUserIdAndTenantId(eq(CREATOR_ID), any())).thenReturn(Optional.empty());

        useCase(TenancyProperties.Mode.SHARED).create(command());

        // Membership still created (so they can switch in), but no role mirrored.
        verify(membershipRepo).save(any(UserTenantMembershipJpaEntity.class));
        verify(userRoleRepo, never()).save(any(UserRoleJpaEntity.class));
    }

    @Test
    void schemaModeKeepsProvisioningPathWithoutAutoMembership() {
        when(tenantRepo.existsBySlug("acme")).thenReturn(false);
        givenSuperAdminCreator();

        Tenant created = useCase(TenancyProperties.Mode.SCHEMA_PER_TENANT).create(command());

        // Non-shared isolation stays on the PROVISIONING saga path.
        assertThat(created.status()).isEqualTo(TenantStatus.PROVISIONING);
        verify(membershipRepo, never()).save(any(UserTenantMembershipJpaEntity.class));
        verify(userRoleRepo, never()).save(any(UserRoleJpaEntity.class));
        // Tenant creation is still audited.
        verify(audit, times(1)).record(any(AuditEvent.class));
    }
}
