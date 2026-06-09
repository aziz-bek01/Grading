package uz.hrlab.grading.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.access.application.UserScopeExpander;
import uz.hrlab.grading.access.infrastructure.RolePermissionRepository;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
import uz.hrlab.grading.access.infrastructure.UserRoleRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.access.domain.MembershipStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Offline unit test proving {@link DevUserAuthorityResolver} now carries the
 * DB-expanded ABAC scope (E4-S0) on its {@link DevUserAuthorityResolver.DevAuthority}
 * result — the dev path mirrors the production OIDC path. Pure Mockito; the
 * scope expansion itself is unit-tested separately in {@code UserScopeExpanderTest}.
 */
@Tag("unit")
class DevUserAuthorityResolverScopeTest {

    private final UserTenantMembershipRepository memberships =
            mock(UserTenantMembershipRepository.class);
    private final UserRoleRepository userRoles = mock(UserRoleRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final RolePermissionRepository rolePermissions = mock(RolePermissionRepository.class);
    private final UserScopeExpander scopeExpander = mock(UserScopeExpander.class);

    private final DevUserAuthorityResolver resolver = new DevUserAuthorityResolver(
            memberships, userRoles, roles, rolePermissions, scopeExpander);

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @Test
    void resolvedAuthorityCarriesDbExpandedScope() {
        UserTenantMembershipJpaEntity membership = new UserTenantMembershipJpaEntity(
                UUID.randomUUID(), userId, tenantId, MembershipStatus.ACTIVE, false);
        when(memberships.findByUserIdAndTenantId(userId, tenantId))
                .thenReturn(Optional.of(membership));
        when(userRoles.findAllByMembershipId(any())).thenReturn(List.of());

        UUID project = UUID.randomUUID();
        UUID dept = UUID.randomUUID();
        when(scopeExpander.resolveProjectIds(eq(userId), eq(tenantId), any()))
                .thenReturn(Set.of(project));
        when(scopeExpander.resolveDepartmentScope(eq(userId), eq(tenantId), any()))
                .thenReturn(Set.of(dept));

        DevUserAuthorityResolver.DevAuthority authority = resolver.resolve(userId, tenantId);

        assertThat(authority.projectIds()).containsExactly(project);
        assertThat(authority.departmentScope()).containsExactly(dept);
    }

    @Test
    void noMembershipYieldsEmptyScope() {
        when(memberships.findByUserIdAndTenantId(userId, tenantId)).thenReturn(Optional.empty());

        DevUserAuthorityResolver.DevAuthority authority = resolver.resolve(userId, tenantId);

        assertThat(authority.projectIds()).isEmpty();
        assertThat(authority.departmentScope()).isEmpty();
    }
}
