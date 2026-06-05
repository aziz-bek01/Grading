package uz.hrlab.grading.tenancy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.AbstractIntegrationTest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Proves the RLS hardening contract from migration {@code 031-rls-nullif.yaml}
 * under a NON-superuser role (the production analogue of {@code grading_runtime}).
 *
 * <p>The default Testcontainers user is a superuser and therefore BYPASSes RLS
 * — which is exactly why the production 22P02 was never reproduced in tests.
 * Here we create a dedicated login role WITHOUT superuser/BYPASSRLS, grant it
 * DML on {@code public.projects}, and connect as that role to assert:
 * <ol>
 *   <li>with {@code app.tenant_id} set to a tenant, the role sees ONLY that
 *       tenant's rows (RLS permits them — the primary fix's GUC works);</li>
 *   <li>with {@code app.tenant_id} unset/empty, the query returns ZERO ROWS
 *       and does NOT raise {@code 22P02} (the blank-safe NULLIF policy) — this
 *       is the behaviour that turns the prod 409 into a safe empty result.</li>
 * </ol>
 */
@Tag("tenant-isolation")
@Tag("integration")
class RlsBlankSafeGucIntegrationTest extends AbstractIntegrationTest {

    private static final String RUNTIME_ROLE = "grading_runtime_test";
    private static final String RUNTIME_PWD = "runtime_test_pwd";

    @Test
    void blankGucReturnsZeroRowsWhileSetGucReturnsTenantRows() throws SQLException {
        UUID tenantA = newSeededTenantId();
        UUID tenantB = newSeededTenantId();

        // Seed one project per tenant as the migrator/superuser (BYPASSRLS).
        insertProject(tenantA, "PRJ-A");
        insertProject(tenantB, "PRJ-B");

        // Create a NON-superuser, NON-BYPASSRLS login role and grant DML.
        jdbcTemplate.execute("DROP ROLE IF EXISTS " + RUNTIME_ROLE);
        jdbcTemplate.execute("CREATE ROLE " + RUNTIME_ROLE
                + " LOGIN PASSWORD '" + RUNTIME_PWD + "' NOSUPERUSER NOBYPASSRLS");
        jdbcTemplate.execute("GRANT USAGE ON SCHEMA public TO " + RUNTIME_ROLE);
        jdbcTemplate.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON public.projects TO "
                + RUNTIME_ROLE);

        String jdbcUrl = POSTGRES.getJdbcUrl();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, RUNTIME_ROLE, RUNTIME_PWD)) {
            // Sanity: this role is genuinely subject to FORCE RLS (not a superuser).
            assertThat(isSuperuser(conn)).isFalse();

            // (1) GUC set to tenant A -> sees exactly tenant A's row.
            setGuc(conn, tenantA.toString());
            assertThat(countProjects(conn))
                    .as("with app.tenant_id set, RLS permits the tenant's own rows")
                    .isEqualTo(1);
            assertThat(singleProjectTenant(conn)).isEqualTo(tenantA);

            // (1b) GUC set to tenant B -> sees exactly tenant B's row, never A's.
            setGuc(conn, tenantB.toString());
            assertThat(singleProjectTenant(conn)).isEqualTo(tenantB);

            // (2) GUC explicitly empty "" -> ZERO rows, NOT a 22P02 error.
            //     This is the exact prod condition (custom GUC reverts to "").
            assertThatCode(() -> {
                setGuc(conn, "");
                assertThat(countProjects(conn))
                        .as("empty app.tenant_id must yield zero rows, never 22P02")
                        .isZero();
            }).doesNotThrowAnyException();

            // (3) GUC reset to default (never set in this fresh statement scope
            //     after RESET) -> still zero rows, still no error.
            assertThatCode(() -> {
                resetGuc(conn);
                assertThat(countProjects(conn))
                        .as("missing app.tenant_id must yield zero rows, never 22P02")
                        .isZero();
            }).doesNotThrowAnyException();
        } finally {
            jdbcTemplate.execute("DROP OWNED BY " + RUNTIME_ROLE);
            jdbcTemplate.execute("DROP ROLE IF EXISTS " + RUNTIME_ROLE);
        }
    }

    private void insertProject(UUID tenantId, String code) {
        jdbcTemplate.update(
                "INSERT INTO public.projects (id, tenant_id, code, name_i18n, status, version) "
                        + "VALUES (?, ?, ?, '{\"ru-RU\":\"X\"}'::jsonb, 'DRAFT', 0)",
                UUID.randomUUID(), tenantId, code);
    }

    private static boolean isSuperuser(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user")) {
            return rs.next() && rs.getBoolean(1);
        }
    }

    private static void setGuc(Connection conn, String value) throws SQLException {
        try (Statement st = conn.createStatement()) {
            // Session-scoped SET (not LOCAL) so it survives across the
            // autocommit statements this test issues on the same connection.
            st.execute("SET app.tenant_id = '" + value + "'");
        }
    }

    private static void resetGuc(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("RESET app.tenant_id");
        }
    }

    private static int countProjects(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM public.projects")) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    private static UUID singleProjectTenant(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT tenant_id FROM public.projects")) {
            return rs.next() ? rs.getObject(1, UUID.class) : null;
        }
    }
}
