package uz.hrlab.grading.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;
import uz.hrlab.grading.access.application.MembershipAuthorityResolver;
import uz.hrlab.grading.access.application.UserScopeExpander;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.infrastructure.UserJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * BE-AUTH-MT-001 — fast, Docker-free proof that the REAL
 * {@link JwtTenantContextResolver#resolve} honours the {@code X-Active-Tenant-Id}
 * header when it is bound on {@link ActiveTenantHeaderHolder} exactly the way the
 * production {@link ActiveTenantHeaderFilter} binds it before JWT auth.
 *
 * <p>This is the precedence harness that complements the Testcontainers-backed
 * {@code JwtTenantContextResolverTest}: the same scenarios, but driven through
 * mocked repositories so they run on any host (the prod host's Docker NPIPE is
 * unreliable). Together they pin: header→THAT tenant, non-member header→no leak,
 * single membership unaffected, and fail-closed unchanged.
 */
@Tag("security")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtTenantContextResolverHeaderUnitTest {

    @Mock UserRepository users;
    @Mock UserTenantMembershipRepository memberships;
    @Mock MembershipAuthorityResolver authorityResolver;
    @Mock UserScopeExpander scopeExpander;

    @AfterEach
    void cleanup() {
        ActiveTenantHeaderHolder.clear();
    }

    private JwtTenantContextResolver newResolver() {
        // Scope expander is a no-op (empty scope) — irrelevant to tenant selection.
        given(scopeExpander.resolveProjectIds(any(), any(), any())).willReturn(Set.of());
        given(scopeExpander.resolveDepartmentScope(any(), any(), any())).willReturn(Set.of());
        given(authorityResolver.expand(any()))
                .willReturn(new MembershipAuthorityResolver.Authority(Set.of(), Set.of(), false));
        return new JwtTenantContextResolver(users, memberships, authorityResolver, scopeExpander);
    }

    private UUID stubUserWithMemberships(String email, UUID... tenantIds) {
        UUID userId = UUID.randomUUID();
        UserJpaEntity user = org.mockito.Mockito.mock(UserJpaEntity.class);
        given(user.getId()).willReturn(userId);
        given(user.getDefaultLocale()).willReturn("en-US");
        given(users.findByEmailIgnoreCase(anyString())).willReturn(Optional.of(user));

        List<UserTenantMembershipJpaEntity> ms = java.util.Arrays.stream(tenantIds)
                .map(t -> new UserTenantMembershipJpaEntity(
                        UUID.randomUUID(), userId, t, MembershipStatus.ACTIVE, false))
                .toList();
        given(memberships.findAllByUserId(userId)).willReturn(ms);
        return userId;
    }

    private static Jwt emailOnlyToken(String email) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("zitadel-" + UUID.randomUUID())
                .claim("email", email)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    void multiMembershipUserWithValidHeaderResolvesToHeaderTenant() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID userId = stubUserWithMemberships("multi@hrlab.uz", tenantA, tenantB);
        JwtTenantContextResolver resolver = newResolver();

        ActiveTenantHeaderHolder.set(tenantB); // what ActiveTenantHeaderFilter does
        TenantContext ctx = resolver.resolve(emailOnlyToken("multi@hrlab.uz"));

        assertThat(ctx.userId()).isEqualTo(userId);
        assertThat(ctx.tenantId())
                .as("valid X-Active-Tenant-Id for a member tenant must select it")
                .isEqualTo(tenantB);
    }

    @Test
    void multiMembershipUserWithNonMemberHeaderDoesNotLeak() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        stubUserWithMemberships("multi2@hrlab.uz", tenantA, tenantB);
        JwtTenantContextResolver resolver = newResolver();

        ActiveTenantHeaderHolder.set(stranger); // user is NOT a member of `stranger`
        TenantContext ctx = resolver.resolve(emailOnlyToken("multi2@hrlab.uz"));

        assertThat(ctx.tenantId())
                .as("a header for a NON-member tenant must NOT resolve — no cross-tenant leak")
                .isNull();
    }

    @Test
    void multiMembershipUserWithNoHeaderAndNoClaimFailsClosed() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        stubUserWithMemberships("multi3@hrlab.uz", tenantA, tenantB);
        JwtTenantContextResolver resolver = newResolver();

        ActiveTenantHeaderHolder.clear(); // no header bound
        TenantContext ctx = resolver.resolve(emailOnlyToken("multi3@hrlab.uz"));

        assertThat(ctx.tenantId())
                .as("no header + >1 membership + no claim must still fail closed")
                .isNull();
    }

    @Test
    void singleMembershipUserIsUnaffectedByNonMemberHeader() {
        UUID only = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        stubUserWithMemberships("single@hrlab.uz", only);
        JwtTenantContextResolver resolver = newResolver();

        ActiveTenantHeaderHolder.set(other);
        TenantContext ctx = resolver.resolve(emailOnlyToken("single@hrlab.uz"));

        assertThat(ctx.tenantId())
                .as("single-membership user resolves to their one tenant regardless of header")
                .isEqualTo(only);
    }

    @Test
    void claimStillWinsWhenHeaderAbsentForMultiMembership() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        stubUserWithMemberships("claim@hrlab.uz", tenantA, tenantB);
        JwtTenantContextResolver resolver = newResolver();

        ActiveTenantHeaderHolder.clear();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("zitadel-" + UUID.randomUUID())
                .claim("email", "claim@hrlab.uz")
                .claim(JwtClaimNames.ACTIVE_TENANT_ID, tenantA.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        TenantContext ctx = resolver.resolve(jwt);
        assertThat(ctx.tenantId())
                .as("with no header, a member-resolvable claim still selects its tenant")
                .isEqualTo(tenantA);
    }

    @Test
    void headerOutranksClaimWhenBothPresentAndBothAreMemberTenants() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        stubUserWithMemberships("both@hrlab.uz", tenantA, tenantB);
        JwtTenantContextResolver resolver = newResolver();

        ActiveTenantHeaderHolder.set(tenantB);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("zitadel-" + UUID.randomUUID())
                .claim("email", "both@hrlab.uz")
                .claim(JwtClaimNames.ACTIVE_TENANT_ID, tenantA.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        TenantContext ctx = resolver.resolve(jwt);
        assertThat(ctx.tenantId())
                .as("header precedence: the SPA tenant switcher (header) wins over a stale claim")
                .isEqualTo(tenantB);
    }
}
