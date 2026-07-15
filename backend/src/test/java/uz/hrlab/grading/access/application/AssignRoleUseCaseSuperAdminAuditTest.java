package uz.hrlab.grading.access.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.domain.RoleScope;
import uz.hrlab.grading.access.infrastructure.RoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
import uz.hrlab.grading.access.infrastructure.UserRoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRoleRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task #6 — end-to-end wiring proof through {@link AssignRoleUseCase}
 * (POST/DELETE role endpoints), the primary manual grant/revoke path. Uses a
 * REAL {@link SuperAdminRoleAuditor} over a mocked {@link AuditService} so the
 * choke point is exercised through the actual use case.
 *
 * <p>Proves: granting {@code HRLAB_SUPER_ADMIN} records BOTH the generic
 * {@code USER_ROLE_ASSIGNED} AND the distinct
 * {@link AuditAction#PLATFORM_SUPER_ADMIN_GRANTED} (actor+target correct);
 * removing it records {@link AuditAction#PLATFORM_SUPER_ADMIN_REVOKED}; and a
 * normal role change records neither super-admin event (no double-audit).
 */
@Tag("security")
class AssignRoleUseCaseSuperAdminAuditTest {

    private static final UUID ACTOR_ID   = UUID.randomUUID();
    private static final UUID TARGET_ID  = UUID.randomUUID();
    private static final UUID TENANT_ID  = UUID.randomUUID();
    private static final UUID MEMBERSHIP_ID = UUID.randomUUID();
    private static final UUID SUPER_ADMIN_ROLE_ID = UUID.randomUUID();
    private static final UUID NORMAL_ROLE_ID = UUID.randomUUID();

    private UserManagementPolicy policy;
    private UserTenantMembershipRepository membershipRepo;
    private UserRoleRepository userRoleRepo;
    private RoleRepository roleRepo;
    private GetUserDetailsQuery detailsQuery;
    private AuditService audit;

    private AssignRoleUseCase useCase;

    private final RoleJpaEntity superAdminRole = new RoleJpaEntity(
            SUPER_ADMIN_ROLE_ID, RoleCodes.HRLAB_SUPER_ADMIN, "HRLab Super Admin", RoleScope.PLATFORM);
    private final RoleJpaEntity normalRole = new RoleJpaEntity(
            NORMAL_ROLE_ID, RoleCodes.CLIENT_HR_SPECIALIST, "HR Specialist", RoleScope.TENANT);

    @BeforeEach
    void setUp() {
        policy = mock(UserManagementPolicy.class);
        membershipRepo = mock(UserTenantMembershipRepository.class);
        userRoleRepo = mock(UserRoleRepository.class);
        roleRepo = mock(RoleRepository.class);
        detailsQuery = mock(GetUserDetailsQuery.class);
        audit = mock(AuditService.class);

        useCase = new AssignRoleUseCase(policy, membershipRepo, userRoleRepo, roleRepo,
                detailsQuery, audit, new SuperAdminRoleAuditor(audit));

        doNothing().when(policy).requireCanManageInTenant(any(), any());
        doNothing().when(policy).requireCanAssignRole(any(), any());
        when(membershipRepo.findByUserIdAndTenantId(TARGET_ID, TENANT_ID))
                .thenReturn(Optional.of(new UserTenantMembershipJpaEntity(
                        MEMBERSHIP_ID, TARGET_ID, TENANT_ID, MembershipStatus.ACTIVE, false)));
        when(roleRepo.findByCode(RoleCodes.HRLAB_SUPER_ADMIN)).thenReturn(Optional.of(superAdminRole));
        when(roleRepo.findByCode(RoleCodes.CLIENT_HR_SPECIALIST)).thenReturn(Optional.of(normalRole));

        TenantContextHolder.set(new TenantContext(ACTOR_ID, TENANT_ID, Set.of(),
                Set.of(RoleCodes.HRLAB_SUPER_ADMIN), Set.of(), Set.of(), false, "ru-RU"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void grantingSuperAdminEmitsBothGenericAndDistinctGrantEvents() {
        when(userRoleRepo.existsByMembershipIdAndRoleId(MEMBERSHIP_ID, SUPER_ADMIN_ROLE_ID))
                .thenReturn(false);

        useCase.assign(TARGET_ID, TENANT_ID, RoleCodes.HRLAB_SUPER_ADMIN);

        List<AuditEvent> events = captureEvents(2);
        assertThat(events).anyMatch(e -> AuditAction.USER_ROLE_ASSIGNED.equals(e.action()));
        AuditEvent granted = events.stream()
                .filter(e -> AuditAction.PLATFORM_SUPER_ADMIN_GRANTED.equals(e.action()))
                .findFirst().orElseThrow();
        assertThat(granted.actorUserId()).isEqualTo(ACTOR_ID);   // who did it
        assertThat(granted.entityId()).isEqualTo(TARGET_ID);      // who received it
        assertThat(granted.tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void grantingNormalRoleDoesNotEmitSuperAdminGrant() {
        when(userRoleRepo.existsByMembershipIdAndRoleId(MEMBERSHIP_ID, NORMAL_ROLE_ID))
                .thenReturn(false);

        useCase.assign(TARGET_ID, TENANT_ID, RoleCodes.CLIENT_HR_SPECIALIST);

        List<AuditEvent> events = captureEvents(1);
        assertThat(events).anyMatch(e -> AuditAction.USER_ROLE_ASSIGNED.equals(e.action()));
        assertThat(events).noneMatch(e ->
                AuditAction.PLATFORM_SUPER_ADMIN_GRANTED.equals(e.action()));
    }

    @Test
    void removingSuperAdminEmitsBothGenericAndDistinctRevokeEvents() {
        UUID userRoleId = UUID.randomUUID();
        when(userRoleRepo.findByIdAndMembershipId(userRoleId, MEMBERSHIP_ID))
                .thenReturn(Optional.of(new UserRoleJpaEntity(userRoleId, MEMBERSHIP_ID, SUPER_ADMIN_ROLE_ID)));
        when(roleRepo.findById(SUPER_ADMIN_ROLE_ID)).thenReturn(Optional.of(superAdminRole));

        useCase.remove(TARGET_ID, TENANT_ID, userRoleId);

        List<AuditEvent> events = captureEvents(2);
        assertThat(events).anyMatch(e -> AuditAction.USER_ROLE_REMOVED.equals(e.action()));
        AuditEvent revoked = events.stream()
                .filter(e -> AuditAction.PLATFORM_SUPER_ADMIN_REVOKED.equals(e.action()))
                .findFirst().orElseThrow();
        assertThat(revoked.actorUserId()).isEqualTo(ACTOR_ID);
        assertThat(revoked.entityId()).isEqualTo(TARGET_ID);
    }

    @Test
    void removingNormalRoleDoesNotEmitSuperAdminRevoke() {
        UUID userRoleId = UUID.randomUUID();
        when(userRoleRepo.findByIdAndMembershipId(userRoleId, MEMBERSHIP_ID))
                .thenReturn(Optional.of(new UserRoleJpaEntity(userRoleId, MEMBERSHIP_ID, NORMAL_ROLE_ID)));
        when(roleRepo.findById(NORMAL_ROLE_ID)).thenReturn(Optional.of(normalRole));

        useCase.remove(TARGET_ID, TENANT_ID, userRoleId);

        List<AuditEvent> events = captureEvents(1);
        assertThat(events).anyMatch(e -> AuditAction.USER_ROLE_REMOVED.equals(e.action()));
        assertThat(events).noneMatch(e ->
                AuditAction.PLATFORM_SUPER_ADMIN_REVOKED.equals(e.action()));
    }

    private List<AuditEvent> captureEvents(int atLeast) {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, atLeastOnce()).record(captor.capture());
        List<AuditEvent> events = captor.getAllValues();
        assertThat(events).hasSizeGreaterThanOrEqualTo(atLeast);
        return events;
    }
}
