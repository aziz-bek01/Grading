package uz.hrlab.grading.security;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.project.domain.ProjectStatus;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Crown-jewel isolation pack for Fix A — a platform Super Admin sees and switches
 * into ALL ACTIVE tenants. Proves, end to end against a real Postgres:
 * <ol>
 *   <li>a platform super admin CAN activate an ACTIVE tenant they hold NO
 *       membership in — the resolver synthesizes a context PINNED to that tenant —
 *       and RLS still scopes their reads to that tenant (under the genuinely
 *       RLS-enforcing {@code grading_runtime} role);</li>
 *   <li>the SAME super admin is DENIED activating a non-ACTIVE
 *       (PROVISIONING/SUSPENDED) tenant they lack a membership in;</li>
 *   <li>a NON-super-admin with no membership in a tenant is STILL 403'd by F-205
 *       (fail-closed path unchanged);</li>
 *   <li>the cross-tenant activation emits a {@code CROSS_TENANT_PLATFORM_ACCESS}
 *       append-only audit row.</li>
 * </ol>
 *
 * <p>The predicate is DB-derived, so the super admin genuinely holds
 * {@code HRLAB_SUPER_ADMIN} on an ACTIVE membership; the non-admin genuinely does
 * not. Nothing here trusts a JWT claim or the target tenant.
 */
@Tag("tenant-isolation")
@Tag("integration")
class PlatformSuperAdminCrossTenantIntegrationTest extends AbstractIntegrationTest {

    @Autowired JwtTenantContextResolver resolver;
    @Autowired TenantContextFilter filter;
    @Autowired ProjectRepository projects;

    private final List<HikariDataSource> openSources = new ArrayList<>();

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        ActiveTenantHeaderHolder.clear();
        openSources.forEach(HikariDataSource::close);
        openSources.clear();
    }

    // ================================================================== //
    // 1) Super admin CAN activate an ACTIVE non-member tenant + RLS scopes //
    // ================================================================== //

    @Test
    void superAdminActivatesActiveNonMemberTenantAndRlsScopesReads() {
        UUID home = seedTenant(UUID.randomUUID());     // super admin's real tenant
        UUID target = seedTenant(UUID.randomUUID());   // ACTIVE, NOT a member
        String email = "sa-cross+" + UUID.randomUUID() + "@hrlab.uz";
        UUID userId = seedSuperAdmin(email, home);

        // Business rows in BOTH tenants (RLS-protected public.projects).
        UUID homeProject = seedProject(home);
        UUID targetProject = seedProject(target);

        // The SPA switcher sends X-Active-Tenant-Id = target. The resolver
        // synthesizes a context pinned to target with the super admin's role set.
        ActiveTenantHeaderHolder.set(target);
        TenantContext ctx = resolver.resolve(emailOnlyToken(email));

        assertThat(ctx.userId()).isEqualTo(userId);
        assertThat(ctx.tenantId())
                .as("super admin activates the ACTIVE non-member target tenant")
                .isEqualTo(target);
        assertThat(ctx.roles()).contains(RoleCodes.HRLAB_SUPER_ADMIN);

        // RLS proof under the deployed grading_runtime role (FORCE RLS, NON-super):
        // binding the resolved tenant id (target) as app.tenant_id makes ONLY the
        // target-tenant project visible; the home project stays hidden. This is the
        // load-bearing "RLS still scopes their reads to that tenant" assertion.
        JdbcTemplate runtime = connectAs("grading_runtime", "grading_runtime_pwd");
        runtime.execute((Connection conn) -> {
            try (Statement st = conn.createStatement()) {
                st.execute("SET app.tenant_id = '" + ctx.tenantId() + "'");
            }
            assertThat(projectVisible(conn, targetProject))
                    .as("target-tenant row is visible under the resolved tenant GUC")
                    .isTrue();
            assertThat(projectVisible(conn, homeProject))
                    .as("home-tenant row stays HIDDEN — the super admin's reads are RLS-scoped "
                            + "to the activated tenant, not widened to all tenants")
                    .isFalse();
            return null;
        });
    }

    // ================================================================== //
    // 2) Super admin DENIED activating a non-ACTIVE non-member tenant     //
    // ================================================================== //

    @Test
    void superAdminDeniedActivatingProvisioningNonMemberTenant() {
        UUID home = seedTenant(UUID.randomUUID());
        UUID provisioning = seedTenantWithStatus(UUID.randomUUID(), "PROVISIONING");
        String email = "sa-prov+" + UUID.randomUUID() + "@hrlab.uz";
        seedSuperAdmin(email, home);

        ActiveTenantHeaderHolder.set(provisioning);
        TenantContext ctx = resolver.resolve(emailOnlyToken(email));

        assertThat(ctx.tenantId())
                .as("a PROVISIONING tenant is never activated via the Fix A carve-out")
                .isNotEqualTo(provisioning);
    }

    @Test
    void superAdminDeniedActivatingSuspendedNonMemberTenant() {
        UUID home = seedTenant(UUID.randomUUID());
        UUID suspended = seedTenantWithStatus(UUID.randomUUID(), "SUSPENDED");
        String email = "sa-susp+" + UUID.randomUUID() + "@hrlab.uz";
        seedSuperAdmin(email, home);

        ActiveTenantHeaderHolder.set(suspended);
        TenantContext ctx = resolver.resolve(emailOnlyToken(email));

        assertThat(ctx.tenantId())
                .as("a SUSPENDED tenant is never activated via the Fix A carve-out")
                .isNotEqualTo(suspended);
    }

    // ================================================================== //
    // 3) Non-super-admin with no membership in X is STILL 403'd (F-205)   //
    // ================================================================== //

    @Test
    void nonSuperAdminWithNoMembershipInTenantIsRejectedByF205() throws Exception {
        UUID own = seedTenant(UUID.randomUUID());
        UUID foreign = seedTenant(UUID.randomUUID());  // ACTIVE, but not a member
        String email = "nonadmin+" + UUID.randomUUID() + "@hrlab.uz";
        UUID userId = seedNonAdmin(email, own);

        // Context claims the foreign tenant is active (as a stale/forged token would).
        TenantContext forged = new TenantContext(userId, foreign, Set.of(),
                Set.of(RoleCodes.CLIENT_HR_DIRECTOR), Set.of("POSITION_READ"),
                Set.of(), false, "ru-RU");
        SecurityContextHolder.getContext().setAuthentication(
                new DevAuthentication(userId.toString(), forged));

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainRan = {false};
        FilterChain chain = (req, resp) -> chainRan[0] = true;

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/projects"), response, chain);

        assertThat(chainRan[0])
                .as("F-205 must short-circuit a non-super-admin acting in a non-member tenant")
                .isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("PERMISSION_DENIED");
    }

    // ================================================================== //
    // 4) Cross-tenant activation emits the CROSS_TENANT_PLATFORM_ACCESS   //
    //    audit event (real filter + real JpaAuditService).                //
    // ================================================================== //

    @Test
    void superAdminCrossTenantActivationEmitsAuditEvent() throws Exception {
        UUID home = seedTenant(UUID.randomUUID());
        UUID target = seedTenant(UUID.randomUUID());   // ACTIVE, not a member
        String email = "sa-audit+" + UUID.randomUUID() + "@hrlab.uz";
        UUID userId = seedSuperAdmin(email, home);

        // Resolver already produced a context pinned to target; drive the REAL
        // filter with it so the real audit service records the trail.
        TenantContext ctx = new TenantContext(userId, target, Set.of(),
                Set.of(RoleCodes.HRLAB_SUPER_ADMIN), Set.of("PROJECT_READ"),
                Set.of(), false, "ru-RU");
        SecurityContextHolder.getContext().setAuthentication(
                new DevAuthentication(userId.toString(), ctx));

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainRan = {false};
        FilterChain chain = (req, resp) -> chainRan[0] = true;

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/positions"), response, chain);

        assertThat(chainRan[0])
                .as("super admin's cross-tenant request proceeds (not 403)")
                .isTrue();
        assertThat(response.getStatus()).isEqualTo(200);

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.system_audit_log "
                        + "WHERE action = 'CROSS_TENANT_PLATFORM_ACCESS' "
                        + "AND actor_user_id = ? AND tenant_id = ? AND entity_id = ?",
                Integer.class, userId, target, target);
        assertThat(rows)
                .as("an append-only CROSS_TENANT_PLATFORM_ACCESS row is written for the target tenant")
                .isEqualTo(1);
    }

    // ----------------------------------------------------------------- //
    // Fixtures + helpers                                                 //
    // ----------------------------------------------------------------- //

    private UUID seedSuperAdmin(String email, UUID homeTenant) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.users (id, email, full_name, status, default_locale, version) "
                        + "VALUES (?, ?, 'Platform Super Admin', 'ACTIVE', 'ru-RU', 0)",
                userId, email);
        UUID membership = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, 'ACTIVE', false, 0)",
                membership, userId, homeTenant);
        jdbcTemplate.update(
                "INSERT INTO public.user_roles (id, user_tenant_membership_id, role_id) "
                        + "SELECT ?, ?, r.id FROM public.roles r WHERE r.code = 'HRLAB_SUPER_ADMIN'",
                UUID.randomUUID(), membership);
        return userId;
    }

    private UUID seedNonAdmin(String email, UUID ownTenant) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.users (id, email, full_name, status, default_locale, version) "
                        + "VALUES (?, ?, 'Client HR Director', 'ACTIVE', 'ru-RU', 0)",
                userId, email);
        UUID membership = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.user_tenant_memberships (id, user_id, tenant_id, status, "
                        + "salary_data_permission, version) VALUES (?, ?, ?, 'ACTIVE', false, 0)",
                membership, userId, ownTenant);
        jdbcTemplate.update(
                "INSERT INTO public.user_roles (id, user_tenant_membership_id, role_id) "
                        + "SELECT ?, ?, r.id FROM public.roles r WHERE r.code = 'CLIENT_HR_DIRECTOR'",
                UUID.randomUUID(), membership);
        return userId;
    }

    private UUID seedTenantWithStatus(UUID tenantId, String status) {
        String slug = "st-" + tenantId.toString().substring(0, 8);
        jdbcTemplate.update(
                "INSERT INTO public.tenants (id, slug, display_name, isolation_mode, schema_name, "
                        + "database_name, status, default_locale, version) "
                        + "VALUES (?, ?, 'Test tenant', 'SCHEMA', ?, NULL, ?, 'ru-RU', 0)",
                tenantId, slug, "tenant_" + slug.replace('-', '_'), status);
        return tenantId;
    }

    private UUID seedProject(UUID tenantId) {
        ProjectJpaEntity project = projects.save(new ProjectJpaEntity(
                UUID.randomUUID(), tenantId,
                "PRJ-" + UUID.randomUUID().toString().substring(0, 8),
                Map.of("ru-RU", "Project"), null, ProjectStatus.ACTIVE, null, null, null));
        return project.getId();
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

    private static boolean projectVisible(Connection conn, UUID projectId) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT 1 FROM public.projects WHERE id = '" + projectId + "'")) {
            return rs.next();
        }
    }

    private JdbcTemplate connectAs(String username, String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(POSTGRES.getJdbcUrl());
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(2);
        ds.setPoolName("sa-cross-rls-" + username);
        openSources.add(ds);
        return new JdbcTemplate(ds);
    }
}
