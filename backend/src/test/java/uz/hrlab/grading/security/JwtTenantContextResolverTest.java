package uz.hrlab.grading.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE-OIDC-001 — proves that a real OIDC access token, carrying ONLY standard
 * claims ({@code sub}, {@code email}) and NONE of grading's domain claims,
 * still resolves to a full {@link TenantContext}: grading user id, active
 * tenant, and the DB-expanded role + permission set.
 *
 * <p>Runs against a real Postgres so the seeded {@code HRLAB_SUPER_ADMIN} role
 * and its {@code role_permissions} grants are exercised end-to-end.
 */
@Tag("integration")
class JwtTenantContextResolverTest extends AbstractIntegrationTest {

    @Autowired
    JwtTenantContextResolver resolver;

    @Test
    void resolvesGradingUserAndPermissionsFromEmailOnlyToken() {
        // ---- Arrange: a tenant, a grading user, an ACTIVE membership, and the
        //      HRLAB_SUPER_ADMIN role (seeded) attached to that membership.
        UUID tenant = seedTenant(UUID.randomUUID());
        UUID userId = UUID.randomUUID();
        String email = "tester+" + userId + "@hrlab.uz";

        jdbcTemplate.update(
                "INSERT INTO public.users (id, email, external_idp_subject, full_name, status, "
                        + "default_locale, version) VALUES (?, ?, ?, ?, 'ACTIVE', 'ru-RU', 0)",
                userId, email, "zitadel|" + userId, "OIDC Tester");

        UUID membershipId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, 'ACTIVE', false, 0)",
                membershipId, userId, tenant);

        jdbcTemplate.update(
                "INSERT INTO public.user_roles (id, user_tenant_membership_id, role_id) "
                        + "SELECT ?, ?, r.id FROM public.roles r WHERE r.code = 'HRLAB_SUPER_ADMIN'",
                UUID.randomUUID(), membershipId);

        // ---- A ZITADEL-style token: standard OIDC claims only, no domain claims.
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("376051031986929669")     // ZITADEL user id, NOT a grading UUID
                .claim("email", email)
                .claim("email_verified", true)
                .claim("name", "OIDC Tester")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        // ---- Act
        TenantContext ctx = resolver.resolve(jwt);

        // ---- Assert: principal mapped to the grading user + active tenant.
        assertThat(ctx.userId()).isEqualTo(userId);
        assertThat(ctx.tenantId()).isEqualTo(tenant);
        // RBAC was expanded from the DB even though the JWT had no roles claim.
        assertThat(ctx.roles()).contains("HRLAB_SUPER_ADMIN");
        assertThat(ctx.permissions())
                .contains("PROJECT_READ", "POSITION_READ", "METHODOLOGY_READ", "EVALUATION_READ");
        // Salary stays false — HRLAB_SUPER_ADMIN does not grant SALARY_*; the
        // membership flag is false.
        assertThat(ctx.salaryPermission()).isFalse();
        assertThat(ctx.permissions()).doesNotContain("SALARY_VIEW");
        // Locale falls back to the user's default when the token has no locale.
        assertThat(ctx.locale()).isEqualTo("ru-RU");
    }

    /**
     * BE-3-FIX latent-bug guard. The previous expansion guard required BOTH
     * {@code roles} AND {@code permissions} claims to be empty before expanding
     * from the DB. An IdP (e.g. Keycloak) that emits a flat {@code roles} claim
     * but NO {@code permissions} claim would therefore have suppressed permission
     * expansion entirely — leaving the principal with ZERO permissions. The fix
     * gates ONLY on {@code permissions}: a roles-only token still gets its
     * permissions DB-expanded for the active tenant.
     */
    @Test
    void rolesClaimWithoutPermissionsClaimStillExpandsPermissionsFromDb() {
        UUID tenant = seedTenant(UUID.randomUUID());
        UUID userId = UUID.randomUUID();
        String email = "rolesonly+" + userId + "@hrlab.uz";

        jdbcTemplate.update(
                "INSERT INTO public.users (id, email, full_name, status, default_locale, version) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'ru-RU', 0)",
                userId, email, "Roles Only");
        UUID membershipId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, 'ACTIVE', false, 0)",
                membershipId, userId, tenant);
        jdbcTemplate.update(
                "INSERT INTO public.user_roles (id, user_tenant_membership_id, role_id) "
                        + "SELECT ?, ?, r.id FROM public.roles r WHERE r.code = 'HRLAB_SUPER_ADMIN'",
                UUID.randomUUID(), membershipId);

        // Token carries a flat roles claim (some custom value) but NO permissions.
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("555")
                .claim("email", email)
                .claim("roles", java.util.List.of("SOME_IDP_ROLE"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        TenantContext ctx = resolver.resolve(jwt);

        assertThat(ctx.tenantId()).isEqualTo(tenant);
        // The IdP-asserted role claim is honoured (kept verbatim)...
        assertThat(ctx.roles()).contains("SOME_IDP_ROLE");
        // ...but permissions are STILL DB-expanded — not suppressed to empty.
        assertThat(ctx.permissions())
                .as("roles-only token must not suppress DB permission expansion")
                .contains("PROJECT_READ", "POSITION_READ");
    }

    @Test
    void unknownEmailYieldsUserlessContextThatFailsClosed() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("999999999999999999")
                .claim("email", "not-provisioned-" + UUID.randomUUID() + "@hrlab.uz")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        TenantContext ctx = resolver.resolve(jwt);

        // No grading user → userId null → downstream requireActive() / me 401/404.
        assertThat(ctx.userId()).isNull();
        assertThat(ctx.tenantId()).isNull();
        assertThat(ctx.permissions()).isEmpty();
    }

    /**
     * BE-3 (2026-06-12, wrong-active-tenant fallthrough closure). With MORE
     * THAN ONE ACTIVE membership and NEITHER an {@code X-Active-Tenant-Id}
     * header NOR an {@code active_tenant_id} claim resolving to a member tenant,
     * the resolver must FAIL CLOSED — {@code tenantId == null}, NO active tenant
     * — instead of silently defaulting to an earliest-created tenant (which
     * leaked a default tenant's data, e.g. an unattended inbox poll firing
     * before the SPA tenant switcher set the header). The user identity is still
     * resolved so {@code /users/me} can list both memberships and the SPA can
     * prompt to select a company-client.
     */
    @Test
    void multipleActiveTenantsWithoutClaimOrHeaderFailsClosedWithNoActiveTenant() {
        UUID tenantA = seedTenant(UUID.randomUUID());
        UUID tenantB = seedTenant(UUID.randomUUID());
        UUID userId = UUID.randomUUID();
        String email = "multi+" + userId + "@hrlab.uz";

        jdbcTemplate.update(
                "INSERT INTO public.users (id, email, full_name, status, default_locale, version) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'en-US', 0)",
                userId, email, "Multi Tenant");
        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, 'ACTIVE', false, 0)",
                UUID.randomUUID(), userId, tenantA);
        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, 'ACTIVE', false, 0)",
                UUID.randomUUID(), userId, tenantB);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("123")
                .claim("email", email)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        TenantContext ctx = resolver.resolve(jwt);

        // Identity resolved...
        assertThat(ctx.userId()).isEqualTo(userId);
        // ...but NO active tenant is chosen — fail closed. Downstream reads
        // return zero rows (RLS GUC unset) / requireActive() rejects.
        assertThat(ctx.tenantId())
                .as("ambiguous multi-tenant context must NOT default to a tenant")
                .isNull();
    }

    /**
     * BE-3 companion: a multi-membership user that DOES declare a valid active
     * tenant via the {@code active_tenant_id} claim still resolves to THAT
     * tenant (and only that tenant) — fail-closed never breaks the legitimate
     * tenant-switch path.
     */
    @Test
    void multipleActiveTenantsWithMatchingClaimResolvesToThatTenant() {
        UUID tenantA = seedTenant(UUID.randomUUID());
        UUID tenantB = seedTenant(UUID.randomUUID());
        UUID userId = UUID.randomUUID();
        String email = "multi2+" + userId + "@hrlab.uz";

        jdbcTemplate.update(
                "INSERT INTO public.users (id, email, full_name, status, default_locale, version) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'en-US', 0)",
                userId, email, "Multi Tenant 2");
        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, 'ACTIVE', false, 0)",
                UUID.randomUUID(), userId, tenantA);
        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, 'ACTIVE', false, 0)",
                UUID.randomUUID(), userId, tenantB);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("123")
                .claim("email", email)
                .claim("active_tenant_id", tenantB.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        TenantContext ctx = resolver.resolve(jwt);

        assertThat(ctx.userId()).isEqualTo(userId);
        assertThat(ctx.tenantId())
                .as("a member-resolvable active_tenant_id claim must select that tenant")
                .isEqualTo(tenantB);
    }

    // =====================================================================
    // BE-AUTH-MT-001 — the X-Active-Tenant-Id HEADER path.
    //
    // PROD BUG: a multi-membership Super Admin (asr) sent a valid
    // X-Active-Tenant-Id for a tenant he ACTIVELY belongs to, but the resolver
    // could not read the header at JWT-auth time (RequestContextHolder was not
    // bound inside the security filter chain), so pickActiveMembership fell
    // through to AMBIGUOUS -> null -> 404 on tenant-scoped by-id reads.
    //
    // The fix makes the header reliably readable via ActiveTenantHeaderHolder
    // (set by ActiveTenantHeaderFilter at HIGHEST precedence). These tests bind
    // the holder EXACTLY as the production filter does, then assert the resolver
    // behaviour. The token here carries NO active_tenant_id claim, so ONLY the
    // header path can rescue the multi-membership user.
    // =====================================================================

    /**
     * A multi-membership user WITH a valid {@code X-Active-Tenant-Id} header (a
     * tenant they ACTIVELY belong to) and NO {@code active_tenant_id} claim
     * resolves to THAT tenant — no longer ambiguous. This is the regression that
     * caused the prod 404s on the approval-request detail read.
     */
    @Test
    void multipleActiveTenantsWithValidHeaderResolvesToHeaderTenant() {
        UUID tenantA = seedTenant(UUID.randomUUID());
        UUID tenantB = seedTenant(UUID.randomUUID());
        UUID userId = UUID.randomUUID();
        String email = "hdr-valid+" + userId + "@hrlab.uz";

        jdbcTemplate.update(
                "INSERT INTO public.users (id, email, full_name, status, default_locale, version) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'en-US', 0)",
                userId, email, "Header Multi");
        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, 'ACTIVE', false, 0)",
                UUID.randomUUID(), userId, tenantA);
        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, 'ACTIVE', false, 0)",
                UUID.randomUUID(), userId, tenantB);

        Jwt jwt = emailOnlyToken(email);

        // The production filter binds the parsed header here, before auth runs.
        ActiveTenantHeaderHolder.set(tenantB);
        try {
            TenantContext ctx = resolver.resolve(jwt);

            assertThat(ctx.userId()).isEqualTo(userId);
            assertThat(ctx.tenantId())
                    .as("a valid X-Active-Tenant-Id header for a member tenant must select it "
                            + "(no longer AMBIGUOUS -> null)")
                    .isEqualTo(tenantB);
        } finally {
            ActiveTenantHeaderHolder.clear();
        }
    }

    /**
     * SECURITY: the SAME header mechanism for a tenant the user does NOT belong to
     * must NOT resolve to it. With no claim and >1 ACTIVE membership, the
     * non-member header is ignored and the resolver falls through to fail-closed
     * (null) — no cross-tenant leak via a forged/stale header.
     */
    @Test
    void multipleActiveTenantsWithHeaderForNonMemberTenantDoesNotLeak() {
        UUID tenantA = seedTenant(UUID.randomUUID());
        UUID tenantB = seedTenant(UUID.randomUUID());
        UUID strangerTenant = seedTenant(UUID.randomUUID()); // user is NOT a member
        UUID userId = UUID.randomUUID();
        String email = "hdr-nonmember+" + userId + "@hrlab.uz";

        jdbcTemplate.update(
                "INSERT INTO public.users (id, email, full_name, status, default_locale, version) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'en-US', 0)",
                userId, email, "Header Stranger");
        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, 'ACTIVE', false, 0)",
                UUID.randomUUID(), userId, tenantA);
        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, 'ACTIVE', false, 0)",
                UUID.randomUUID(), userId, tenantB);

        Jwt jwt = emailOnlyToken(email);

        ActiveTenantHeaderHolder.set(strangerTenant);
        try {
            TenantContext ctx = resolver.resolve(jwt);

            assertThat(ctx.userId()).isEqualTo(userId);
            assertThat(ctx.tenantId())
                    .as("a header pointing at a NON-member tenant must NOT resolve to it — "
                            + "fall through to fail-closed, never leak")
                    .isNull();
            assertThat(ctx.tenantId()).isNotEqualTo(strangerTenant);
        } finally {
            ActiveTenantHeaderHolder.clear();
        }
    }

    /**
     * The header path NEVER overrides the membership cross-check even when it
     * matches a real member tenant: a single-membership user is unaffected (the
     * one membership wins) and remains correct whether or not a header is present.
     */
    @Test
    void singleMembershipUserIsUnaffectedByHeader() {
        UUID tenant = seedTenant(UUID.randomUUID());
        UUID otherTenant = seedTenant(UUID.randomUUID());
        UUID userId = UUID.randomUUID();
        String email = "single+" + userId + "@hrlab.uz";

        jdbcTemplate.update(
                "INSERT INTO public.users (id, email, full_name, status, default_locale, version) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'en-US', 0)",
                userId, email, "Single Membership");
        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, 'ACTIVE', false, 0)",
                UUID.randomUUID(), userId, tenant);

        Jwt jwt = emailOnlyToken(email);

        // A header for a tenant the single-membership user does NOT belong to must
        // not move them; their one membership stays authoritative.
        ActiveTenantHeaderHolder.set(otherTenant);
        try {
            TenantContext ctx = resolver.resolve(jwt);
            assertThat(ctx.tenantId())
                    .as("single-membership user must resolve to their one tenant regardless of "
                            + "a non-member header")
                    .isEqualTo(tenant);
        } finally {
            ActiveTenantHeaderHolder.clear();
        }
    }

    /**
     * Fail-closed is UNCHANGED: multi-membership, NO header bound, NO claim →
     * still null. This pins that the fix only makes a PRESENT header readable and
     * does not auto-pick a default tenant.
     */
    @Test
    void multipleActiveTenantsWithNoHeaderAndNoClaimStillFailsClosed() {
        UUID tenantA = seedTenant(UUID.randomUUID());
        UUID tenantB = seedTenant(UUID.randomUUID());
        UUID userId = UUID.randomUUID();
        String email = "noheader+" + userId + "@hrlab.uz";

        jdbcTemplate.update(
                "INSERT INTO public.users (id, email, full_name, status, default_locale, version) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'en-US', 0)",
                userId, email, "No Header Multi");
        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, 'ACTIVE', false, 0)",
                UUID.randomUUID(), userId, tenantA);
        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, 'ACTIVE', false, 0)",
                UUID.randomUUID(), userId, tenantB);

        // Ensure NO header is bound for this thread.
        ActiveTenantHeaderHolder.clear();
        TenantContext ctx = resolver.resolve(emailOnlyToken(email));

        assertThat(ctx.userId()).isEqualTo(userId);
        assertThat(ctx.tenantId())
                .as("no header + >1 membership + no claim must still fail closed (null)")
                .isNull();
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
}
