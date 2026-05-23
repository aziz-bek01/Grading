package uz.hrlab.grading.db;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import uz.hrlab.grading.AbstractIntegrationTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Defense-in-depth proof for F-04 (security review §F-04).
 *
 * <p>Connects to the Testcontainers Postgres as the three runtime roles
 * created by {@code 005-db-role-grants.yaml} and asserts the grant matrix
 * documented in {@code db/role-grants-matrix.md}:
 *
 * <ul>
 *   <li><b>grading_runtime</b>: INSERT + SELECT on {@code system_audit_log}
 *       succeed; UPDATE and DELETE fail with PostgreSQL permission error.</li>
 *   <li><b>grading_audit_reader</b>: SELECT succeeds; INSERT fails.</li>
 * </ul>
 *
 * <p>The test relies on the {@code test-roles} Liquibase context which is
 * enabled in {@code application-test.yml}. That context attaches LOGIN +
 * dev passwords to the three NOLOGIN roles so this test can dial them
 * directly. Production environments must NEVER load {@code test-roles}.
 */
@Tag("audit")
@Tag("integration")
class AuditRoleGrantsTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate adminJdbc; // connected as the bootstrap superuser

    @Autowired
    Environment env;

    private final List<HikariDataSource> openSources = new ArrayList<>();

    @AfterEach
    void closeOpenDataSources() {
        openSources.forEach(HikariDataSource::close);
        openSources.clear();
    }

    // ----------------------------------------------------------------- helpers

    private JdbcTemplate connectAs(String username, String password) {
        // Spring populates spring.datasource.url from the Testcontainers
        // @ServiceConnection on the abstract base class, so we read it from
        // the Environment instead of touching the package-private POSTGRES
        // field on the base class.
        String url = env.getRequiredProperty("spring.datasource.url");
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(2);
        ds.setPoolName("rolegrants-" + username);
        openSources.add(ds);
        return new JdbcTemplate(ds);
    }

    private UUID insertAuditRowAsRuntime(JdbcTemplate runtimeJdbc, UUID tenantId) {
        UUID id = UUID.randomUUID();
        runtimeJdbc.update(
                "INSERT INTO public.system_audit_log "
                        + "(id, tenant_id, action, created_at, hash_current) "
                        + "VALUES (?, ?, 'TEST_INSERT', now(), 'h0')",
                id, tenantId);
        return id;
    }

    // -------------------------------------------------------------- assertions

    @Test
    void runtimeRoleCanInsertAndSelectAuditButNotMutate() {
        JdbcTemplate runtime = connectAs("grading_runtime", "grading_runtime_pwd");
        UUID tenantId = UUID.randomUUID();

        // (1) INSERT into system_audit_log → must succeed.
        UUID id = insertAuditRowAsRuntime(runtime, tenantId);

        // (2) SELECT must succeed.
        Long count = runtime.queryForObject(
                "SELECT COUNT(*) FROM public.system_audit_log WHERE id = ?",
                Long.class, id);
        assertThat(count).isEqualTo(1L);

        // (3) UPDATE must fail with permission denied.
        assertThatThrownBy(() -> runtime.update(
                "UPDATE public.system_audit_log SET reason = 'tampered' WHERE id = ?", id))
                .as("grading_runtime must NOT be allowed to UPDATE system_audit_log")
                .hasMessageContaining("permission denied");

        // (4) DELETE must fail with permission denied.
        assertThatThrownBy(() -> runtime.update(
                "DELETE FROM public.system_audit_log WHERE id = ?", id))
                .as("grading_runtime must NOT be allowed to DELETE from system_audit_log")
                .hasMessageContaining("permission denied");

        // (5) TRUNCATE must fail too (covers the bulk-wipe attack vector).
        assertThatThrownBy(() -> runtime.execute("TRUNCATE public.system_audit_log"))
                .as("grading_runtime must NOT be allowed to TRUNCATE system_audit_log")
                .hasMessageContaining("permission denied");
    }

    @Test
    void runtimeRoleHasSameLockdownOnTenantAuditLogs() {
        JdbcTemplate runtime = connectAs("grading_runtime", "grading_runtime_pwd");
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        runtime.update(
                "INSERT INTO public.tenant_audit_logs "
                        + "(id, tenant_id, action, created_at, hash_current) "
                        + "VALUES (?, ?, 'TEST_INSERT', now(), 'h0')",
                id, tenantId);

        assertThatThrownBy(() -> runtime.update(
                "UPDATE public.tenant_audit_logs SET reason = 'tampered' WHERE id = ?", id))
                .hasMessageContaining("permission denied");
        assertThatThrownBy(() -> runtime.update(
                "DELETE FROM public.tenant_audit_logs WHERE id = ?", id))
                .hasMessageContaining("permission denied");
    }

    @Test
    void runtimeRoleCanStillMutateBusinessTables() {
        // Sanity check: lockdown is scoped to audit tables. Business DML on
        // tenants/users/etc. must still work for the runtime role.
        JdbcTemplate runtime = connectAs("grading_runtime", "grading_runtime_pwd");

        UUID id = UUID.randomUUID();
        runtime.update(
                "INSERT INTO public.tenants "
                        + "(id, slug, display_name, isolation_mode, schema_name, status,"
                        + " default_locale, version, created_at) "
                        + "VALUES (?, ?, 'Probe Co', 'SCHEMA', 'tenant_probe', 'ACTIVE',"
                        + " 'ru-RU', 0, now())",
                id, "probe-" + id);

        runtime.update("UPDATE public.tenants SET display_name = 'Renamed' WHERE id = ?", id);
        runtime.update("DELETE FROM public.tenants WHERE id = ?", id);
    }

    @Test
    void auditReaderRoleCanSelectAuditButNotInsert() {
        JdbcTemplate runtime = connectAs("grading_runtime", "grading_runtime_pwd");
        JdbcTemplate reader = connectAs("grading_audit_reader", "grading_audit_reader_pwd");

        UUID tenantId = UUID.randomUUID();
        UUID id = insertAuditRowAsRuntime(runtime, tenantId);

        // SELECT succeeds.
        Long visible = reader.queryForObject(
                "SELECT COUNT(*) FROM public.system_audit_log WHERE id = ?",
                Long.class, id);
        assertThat(visible).isEqualTo(1L);

        // INSERT fails.
        assertThatThrownBy(() -> reader.update(
                "INSERT INTO public.system_audit_log "
                        + "(id, tenant_id, action, created_at, hash_current) "
                        + "VALUES (?, ?, 'EVIL_INSERT', now(), 'h0')",
                UUID.randomUUID(), tenantId))
                .as("grading_audit_reader must NOT be allowed to INSERT into system_audit_log")
                .hasMessageContaining("permission denied");
    }

    @Test
    void auditReaderRoleHasNoAccessToBusinessTables() {
        JdbcTemplate reader = connectAs("grading_audit_reader", "grading_audit_reader_pwd");

        assertThatThrownBy(() -> reader.queryForObject(
                "SELECT COUNT(*) FROM public.tenants", Long.class))
                .as("grading_audit_reader must NOT read business tables")
                .hasMessageContaining("permission denied");
    }

    @Test
    void grantMatrixIsRecordedInInformationSchema() {
        // information_schema.table_privileges is the canonical assertion
        // referenced by F-04 acceptance criteria. We assert the exact set
        // of granted privileges per (role, table).
        @SuppressWarnings("unchecked")
        List<String> runtimeAuditPrivs = adminJdbc.queryForList(
                "SELECT privilege_type FROM information_schema.table_privileges "
                        + "WHERE grantee = 'grading_runtime' "
                        + "  AND table_schema = 'public' "
                        + "  AND table_name = 'system_audit_log' "
                        + "ORDER BY privilege_type",
                String.class);
        assertThat(runtimeAuditPrivs)
                .as("grading_runtime on system_audit_log: only SELECT + INSERT allowed")
                .containsExactlyInAnyOrder("SELECT", "INSERT");

        @SuppressWarnings("unchecked")
        List<String> readerAuditPrivs = adminJdbc.queryForList(
                "SELECT privilege_type FROM information_schema.table_privileges "
                        + "WHERE grantee = 'grading_audit_reader' "
                        + "  AND table_schema = 'public' "
                        + "  AND table_name = 'system_audit_log' "
                        + "ORDER BY privilege_type",
                String.class);
        assertThat(readerAuditPrivs)
                .as("grading_audit_reader on system_audit_log: only SELECT allowed")
                .containsExactly("SELECT");
    }

}
