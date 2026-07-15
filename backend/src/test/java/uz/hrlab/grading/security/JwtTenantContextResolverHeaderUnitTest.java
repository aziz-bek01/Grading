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
import uz.hrlab.grading.access.application.PlatformSuperAdminChecker;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.access.application.UserScopeExpander;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.infrastructure.UserJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.domain.TenantStatus;
import uz.hrlab.grading.tenancy.infrastructure.TenantJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

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
    @Mock PlatformSuperAdminChecker superAdminChecker;
    @Mock TenantRepository tenants;

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
        // These header/precedence scenarios are all NON-super-admins, so the Fix A
        // predicate is false — the cross-tenant carve-out never opens and every
        // fail-closed assertion below is the UNCHANGED member-only path.
        given(superAdminChecker.isPlatformSuperAdmin(any())).willReturn(false);
        return new JwtTenantContextResolver(
                users, memberships, authorityResolver, scopeExpander, superAdminChecker, tenants);
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

    // =====================================================================
    // Fix A — platform super-admin cross-tenant activation (fast, mocked).
    // =====================================================================

    private void stubTenant(UUID tenantId, TenantStatus status) {
        TenantJpaEntity t = org.mockito.Mockito.mock(TenantJpaEntity.class);
        given(t.getStatus()).willReturn(status);
        given(tenants.findById(tenantId)).willReturn(Optional.of(t));
    }

    /**
     * A platform super admin with a home membership sends {@code X-Active-Tenant-Id}
     * for an ACTIVE tenant they are NOT a member of → the resolver synthesizes a
     * context PINNED to that target tenant (so the RLS GUC binds there), with the
     * roles/permissions expanded from their OWN HRLAB_SUPER_ADMIN membership.
     */
    @Test
    void platformSuperAdminActivatesActiveNonMemberTenantViaHeader() {
        UUID home = UUID.randomUUID();
        UUID target = UUID.randomUUID(); // ACTIVE, super admin is NOT a member
        UUID userId = stubUserWithMemberships("sa@hrlab.uz", home);
        JwtTenantContextResolver resolver = newResolver();

        // Real predicate holds (DB-derived) + the target is ACTIVE + the home
        // membership carries HRLAB_SUPER_ADMIN (so authority expands to it).
        given(superAdminChecker.isPlatformSuperAdmin(userId)).willReturn(true);
        stubTenant(target, TenantStatus.ACTIVE);
        given(authorityResolver.expand(any())).willReturn(
                new MembershipAuthorityResolver.Authority(
                        Set.of(RoleCodes.HRLAB_SUPER_ADMIN), Set.of("PROJECT_READ"), false));

        ActiveTenantHeaderHolder.set(target);
        TenantContext ctx = resolver.resolve(emailOnlyToken("sa@hrlab.uz"));

        assertThat(ctx.userId()).isEqualTo(userId);
        assertThat(ctx.tenantId())
                .as("super admin activates the ACTIVE non-member target tenant")
                .isEqualTo(target);
        assertThat(ctx.roles()).contains(RoleCodes.HRLAB_SUPER_ADMIN);
        assertThat(ctx.permissions()).contains("PROJECT_READ");
    }

    /**
     * The SAME super admin sends a header for a NON-member tenant that is NOT
     * ACTIVE (e.g. PROVISIONING/SUSPENDED) → the carve-out does NOT open; they are
     * NOT activated into it. With a single home membership they fall back to home;
     * the assertion is simply "never the requested non-active tenant".
     */
    @Test
    void platformSuperAdminDeniedForNonActiveNonMemberTenant() {
        UUID home = UUID.randomUUID();
        UUID target = UUID.randomUUID(); // NON-member, NON-active
        UUID userId = stubUserWithMemberships("sa2@hrlab.uz", home);
        JwtTenantContextResolver resolver = newResolver();

        given(superAdminChecker.isPlatformSuperAdmin(userId)).willReturn(true);
        stubTenant(target, TenantStatus.SUSPENDED);

        ActiveTenantHeaderHolder.set(target);
        TenantContext ctx = resolver.resolve(emailOnlyToken("sa2@hrlab.uz"));

        assertThat(ctx.tenantId())
                .as("a non-ACTIVE tenant is never activated via the Fix A carve-out")
                .isNotEqualTo(target);
    }

    /**
     * F-3 (hardening): the synthesized cross-tenant context defaults
     * {@code salaryPermission} to FALSE even when the super admin holds the salary
     * gate on their HOME membership — the strict salary-separation rule means they
     * do not see salary in a tenant they are not a member of. (Their salary access
     * in tenants they ARE a member of never reaches synthesize, so is unaffected.)
     */
    @Test
    void synthesizedCrossTenantContextForcesSalaryPermissionOff() {
        UUID home = UUID.randomUUID();
        UUID target = UUID.randomUUID(); // ACTIVE, non-member
        UUID userId = stubUserWithMemberships("sa-sal@hrlab.uz", home);
        JwtTenantContextResolver resolver = newResolver();

        given(superAdminChecker.isPlatformSuperAdmin(userId)).willReturn(true);
        stubTenant(target, TenantStatus.ACTIVE);
        // The HOME membership carries salary permission == TRUE.
        given(authorityResolver.expand(any())).willReturn(
                new MembershipAuthorityResolver.Authority(
                        Set.of(RoleCodes.HRLAB_SUPER_ADMIN), Set.of("PROJECT_READ"), true));

        ActiveTenantHeaderHolder.set(target);
        TenantContext ctx = resolver.resolve(emailOnlyToken("sa-sal@hrlab.uz"));

        assertThat(ctx.tenantId()).isEqualTo(target);
        assertThat(ctx.salaryPermission())
                .as("cross-tenant synthesized context must default salary OFF (F-3)")
                .isFalse();
    }

    /**
     * A NON-super-admin with a header for an ACTIVE non-member tenant is NEVER
     * activated into it — the predicate is false so the carve-out cannot open,
     * proving no other role can ride Fix A into another tenant.
     */
    @Test
    void nonSuperAdminNeverActivatesActiveNonMemberTenant() {
        UUID home = UUID.randomUUID();
        UUID target = UUID.randomUUID(); // ACTIVE, but caller is not a super admin
        stubUserWithMemberships("nonsa@hrlab.uz", home);
        JwtTenantContextResolver resolver = newResolver(); // predicate stubbed false

        // Even if the tenant IS active, the false predicate must block activation.
        stubTenant(target, TenantStatus.ACTIVE);

        ActiveTenantHeaderHolder.set(target);
        TenantContext ctx = resolver.resolve(emailOnlyToken("nonsa@hrlab.uz"));

        assertThat(ctx.tenantId())
                .as("no non-super-admin may be activated into a non-member tenant")
                .isNotEqualTo(target)
                .isEqualTo(home); // single membership → their own tenant, unchanged
    }
}
