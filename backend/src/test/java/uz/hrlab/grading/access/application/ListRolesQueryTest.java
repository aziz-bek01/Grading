package uz.hrlab.grading.access.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.access.api.RoleResponse;
import uz.hrlab.grading.access.domain.RoleScope;
import uz.hrlab.grading.access.infrastructure.RoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Offline unit tests for {@link ListRolesQuery} (slice E1, E3-aware). Proves the
 * catalog uses the tenant-scoped finder (system roles + own-tenant custom roles),
 * derives {@code is_system}/{@code is_custom} from the entity, and that a TENANT
 * custom role is assignable by the tenant admin.
 */
@Tag("unit")
class ListRolesQueryTest {

    private RoleRepository roleRepo;
    private ListRolesQuery query;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        roleRepo = mock(RoleRepository.class);
        UserManagementPolicy policy =
                new UserManagementPolicy(mock(UserTenantMembershipRepository.class));
        query = new ListRolesQuery(policy, roleRepo);

        TenantContextHolder.set(new TenantContext(
                actorId, tenantId, Set.of(), Set.of(RoleCodes.CLIENT_COMPANY_ADMIN),
                Set.of(PermissionCodes.USER_ROLE_ASSIGN), Set.of(), false, "ru-RU",
                Set.of(tenantId)));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void catalogUsesTenantScopedFinderAndDerivesCustomFlagAndAssignability() {
        UUID systemRoleId = UUID.randomUUID();
        UUID customRoleId = UUID.randomUUID();
        RoleJpaEntity systemRole =
                new RoleJpaEntity(systemRoleId, RoleCodes.CLIENT_HR_DIRECTOR,
                        "Client HR Director", RoleScope.TENANT);
        RoleJpaEntity customRole =
                RoleJpaEntity.newCustom(customRoleId, tenantId, "REVIEWER", "Reviewer", null);

        when(roleRepo.findByIsSystemTrueOrTenantIdOrderByScopeAscCodeAsc(tenantId))
                .thenReturn(List.of(systemRole, customRole));

        List<RoleResponse> out = query.list(false);

        // Tenant-scoped finder was used with the ctx tenant (not a tenant-agnostic findAll).
        verify(roleRepo).findByIsSystemTrueOrTenantIdOrderByScopeAscCodeAsc(tenantId);

        RoleResponse system = out.stream()
                .filter(r -> r.code().equals(RoleCodes.CLIENT_HR_DIRECTOR)).findFirst().orElseThrow();
        assertThat(system.isSystem()).isTrue();
        assertThat(system.isCustom()).isFalse();
        // `id` is populated from the role PK (custom-role edit/delete address it).
        assertThat(system.id()).isEqualTo(systemRoleId);

        RoleResponse custom = out.stream()
                .filter(r -> r.code().equals("REVIEWER")).findFirst().orElseThrow();
        assertThat(custom.isSystem()).isFalse();
        assertThat(custom.isCustom()).isTrue();
        assertThat(custom.scope()).isEqualTo("TENANT");
        // The custom-role id is exposed so the FE can PUT/DELETE /roles/{id}.
        assertThat(custom.id()).isEqualTo(customRoleId);
        // A TENANT-scope custom role is assignable by the tenant admin.
        assertThat(custom.assignableByCaller()).isTrue();
        assertThat(custom.reasonIfNot()).isNull();
    }

    @Test
    void assignableOnlyFiltersOutNonAssignableRoles() {
        // A PLATFORM HRLAB_ system role is NOT assignable by a tenant admin.
        RoleJpaEntity hrlab =
                new RoleJpaEntity(UUID.randomUUID(), RoleCodes.HRLAB_CONSULTANT,
                        "HRLab Consultant", RoleScope.PLATFORM);
        RoleJpaEntity custom =
                RoleJpaEntity.newCustom(UUID.randomUUID(), tenantId, "REVIEWER", "Reviewer", null);

        when(roleRepo.findByIsSystemTrueOrTenantIdOrderByScopeAscCodeAsc(tenantId))
                .thenReturn(List.of(hrlab, custom));

        List<RoleResponse> out = query.list(true);

        assertThat(out).extracting(RoleResponse::code).containsExactly("REVIEWER");
    }
}
