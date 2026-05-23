package uz.hrlab.grading.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import uz.hrlab.grading.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies all Liquibase changesets and seeds run cleanly. */
class LiquibaseMigrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void controlPlaneTablesExist() {
        assertThat(tableExists("tenants")).isTrue();
        assertThat(tableExists("client_companies")).isTrue();
        assertThat(tableExists("users")).isTrue();
        assertThat(tableExists("user_tenant_memberships")).isTrue();
        assertThat(tableExists("user_project_assignments")).isTrue();
        assertThat(tableExists("user_roles")).isTrue();
        assertThat(tableExists("roles")).isTrue();
        assertThat(tableExists("permissions")).isTrue();
        assertThat(tableExists("role_permissions")).isTrue();
        assertThat(tableExists("localization_messages")).isTrue();
        assertThat(tableExists("system_audit_log")).isTrue();
        assertThat(tableExists("tenant_audit_logs")).isTrue();
    }

    @Test
    void defaultPermissionsSeeded() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.permissions WHERE code IN ("
                        + "'TENANT_CREATE','POSITION_READ','SALARY_VIEW','AUDIT_READ')",
                Long.class);
        assertThat(count).isEqualTo(4L);
    }

    @Test
    void defaultRolesSeeded() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.roles", Long.class);
        // 11 roles per architecture §8.3.
        assertThat(count).isGreaterThanOrEqualTo(11L);
    }

    @Test
    void defaultLocalesSeeded() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.localization_messages WHERE locale IN ("
                        + "'ru-RU','uz-Cyrl-UZ','uz-Latn-UZ','en-US')",
                Long.class);
        assertThat(count).isEqualTo(4L);
    }

    @Test
    void tenantsHasIsolationModeCheck() {
        // Insert a row that violates chk_tenants_isolation_target — must fail.
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                jdbc.update("INSERT INTO public.tenants (slug, display_name, isolation_mode, "
                        + "schema_name, database_name, status, default_locale, version) "
                        + "VALUES ('bad','Bad','SCHEMA',NULL,NULL,'ACTIVE','ru-RU',0)")
        ).hasMessageContaining("chk_tenants_isolation_target");
    }

    private boolean tableExists(String name) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema='public' AND table_name=?",
                Long.class, name);
        return count != null && count == 1L;
    }
}
