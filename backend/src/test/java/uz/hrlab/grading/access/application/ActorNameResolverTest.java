package uz.hrlab.grading.access.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.infrastructure.UserJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * BE-8 — tenant isolation tests for the shared {@link ActorNameResolver}.
 *
 * <p>{@code public.users} has NO tenant_id, so a bare findById would resolve a
 * name for any tenant. The resolver gates every resolution on an ACTIVE
 * {@code user_tenant_memberships} row for the active tenant — a cross-tenant /
 * non-member id must resolve to {@code null}, never a foreign name (review R3/R4).
 */
@Tag("security")
class ActorNameResolverTest {

    private UserRepository users;
    private UserTenantMembershipRepository memberships;
    private ActorNameResolver resolver;

    private final UUID tenantA = UUID.randomUUID();

    @BeforeEach
    void setup() {
        users = mock(UserRepository.class);
        memberships = mock(UserTenantMembershipRepository.class);
        resolver = new ActorNameResolver(users, memberships);
    }

    @Test
    void activeMemberResolvesToFullName() {
        UUID userId = UUID.randomUUID();
        given(memberships.findByUserIdAndTenantId(eq(userId), eq(tenantA)))
                .willReturn(Optional.of(membership(userId, tenantA, MembershipStatus.ACTIVE)));
        UserJpaEntity u = mock(UserJpaEntity.class);
        given(u.getFullName()).willReturn("Aziz Karimov");
        given(users.findById(eq(userId))).willReturn(Optional.of(u));

        assertThat(resolver.resolve(tenantA, userId)).isEqualTo("Aziz Karimov");
    }

    @Test
    void userWithoutMembershipInActiveTenantResolvesToNull() {
        UUID foreignUser = UUID.randomUUID();
        // No membership row in tenant A — even though the global users table
        // would have the name, it must NOT leak across tenants.
        given(memberships.findByUserIdAndTenantId(eq(foreignUser), eq(tenantA)))
                .willReturn(Optional.empty());

        assertThat(resolver.resolve(tenantA, foreignUser)).isNull();
    }

    @Test
    void nonActiveMembershipResolvesToNull() {
        UUID userId = UUID.randomUUID();
        given(memberships.findByUserIdAndTenantId(eq(userId), eq(tenantA)))
                .willReturn(Optional.of(membership(userId, tenantA, MembershipStatus.REVOKED)));

        assertThat(resolver.resolve(tenantA, userId)).isNull();
    }

    @Test
    void nullInputsResolveToNull() {
        assertThat(resolver.resolve(null, UUID.randomUUID())).isNull();
        assertThat(resolver.resolve(tenantA, null)).isNull();
    }

    @Test
    void batchResolvesOnlyActiveMembersOfTheTenant() {
        UUID member = UUID.randomUUID();
        UUID foreigner = UUID.randomUUID(); // not a member of tenant A

        given(memberships.findByTenantIdAndUserIdIn(eq(tenantA), any())).willReturn(List.of(
                membership(member, tenantA, MembershipStatus.ACTIVE)));

        UserJpaEntity u = mock(UserJpaEntity.class);
        given(u.getId()).willReturn(member);
        given(u.getFullName()).willReturn("Active Member");
        given(users.findAllById(any())).willReturn(List.of(u));

        Map<UUID, String> names = resolver.resolveAll(tenantA, Set.of(member, foreigner));

        assertThat(names).containsEntry(member, "Active Member");
        assertThat(names).doesNotContainKey(foreigner); // never leak a foreign id's name
    }

    @Test
    void batchWithEmptyInputYieldsEmptyMap() {
        assertThat(resolver.resolveAll(tenantA, Set.of())).isEmpty();
        assertThat(resolver.resolveAll(tenantA, null)).isEmpty();
        assertThat(resolver.resolveAll(null, Set.of(UUID.randomUUID()))).isEmpty();
    }

    private UserTenantMembershipJpaEntity membership(UUID userId, UUID tenantId, MembershipStatus status) {
        return new UserTenantMembershipJpaEntity(UUID.randomUUID(), userId, tenantId, status, false);
    }
}
