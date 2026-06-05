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

    @Test
    void multipleActiveTenantsWithoutClaimLeavesTenantNullForSwitcher() {
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

        // User resolved, but no active tenant pinned — SPA renders the switcher;
        // /users/me still lists both memberships.
        assertThat(ctx.userId()).isEqualTo(userId);
        assertThat(ctx.tenantId()).isNull();
    }
}
