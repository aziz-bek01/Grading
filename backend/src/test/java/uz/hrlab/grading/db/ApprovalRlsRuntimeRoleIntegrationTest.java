package uz.hrlab.grading.db;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import uz.hrlab.grading.AbstractIntegrationTest;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE-2 (2026-06-12) — RLS verification for the approval tables under the REAL
 * runtime role.
 *
 * <p>Proves that {@code approval_requests} / {@code approval_steps} /
 * {@code approval_decisions} are genuinely filtered by FORCE ROW LEVEL SECURITY
 * + the blank-safe NULLIF policy (changelogs 030 / 031) when read by
 * {@code grading_runtime} — the role the deployed dev/demo/prod runtime uses
 * after BE-1. This is the database-layer second line of defense behind the
 * explicit tenant predicates; the production leak slipped through because the
 * runtime ran as the bootstrap superuser (BYPASSRLS), so FORCE RLS never engaged.
 *
 * <p>Method:
 * <ol>
 *   <li>seed tenant A + tenant B approval graphs via the bootstrap superuser
 *       ({@code adminJdbc}, which bypasses RLS for setup);</li>
 *   <li>connect as {@code grading_runtime} (NON-superuser, NON-bypassrls,
 *       provisioned with LOGIN by the {@code test-roles} context);</li>
 *   <li>bind {@code app.tenant_id = A} on that connection (the
 *       {@code RlsTenantSessionAspect} GUC path) and assert a RAW read returns
 *       ZERO tenant-B rows across all three approval tables.</li>
 * </ol>
 *
 * <p>Reuses the {@code connectAs(...)} two-role scaffolding from
 * {@link AuditRoleGrantsTest} (same package) — no forked RLS harness.
 */
@Tag("tenant-isolation")
@Tag("security")
@Tag("integration")
class ApprovalRlsRuntimeRoleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate adminJdbc; // bootstrap superuser — bypasses RLS, used for setup only

    private final List<HikariDataSource> openSources = new ArrayList<>();

    @AfterEach
    void closeOpenDataSources() {
        openSources.forEach(HikariDataSource::close);
        openSources.clear();
    }

    @Test
    void runtimeRoleSeesOnlyBoundTenantApprovalRows() {
        UUID tenantA = newSeededTenantId();
        UUID tenantB = newSeededTenantId();

        ApprovalGraph a = seedApprovalGraph(tenantA);
        ApprovalGraph b = seedApprovalGraph(tenantB);

        // Sanity: the superuser (bypassrls) sees BOTH tenants' rows.
        assertThat(countAsAdmin("approval_requests")).isGreaterThanOrEqualTo(2);

        JdbcTemplate runtime = connectAs("grading_runtime", "grading_runtime_pwd");

        // Bind app.tenant_id = A on the runtime connection, then RAW-read every
        // approval table. SET + SELECT must share ONE physical connection (RLS
        // GUC is connection-scoped) — exactly what RlsTenantSessionAspect does
        // with SET LOCAL inside the request transaction.
        runtime.execute((Connection conn) -> {
            try (Statement st = conn.createStatement()) {
                st.execute("SET app.tenant_id = '" + tenantA + "'");
            }

            // requests: only A's request id is visible; B's is filtered out.
            List<UUID> visibleRequests = readUuidColumn(conn,
                    "SELECT id FROM public.approval_requests");
            assertThat(visibleRequests)
                    .as("runtime bound to tenant A must see ONLY A's approval_requests")
                    .contains(a.requestId())
                    .doesNotContain(b.requestId());

            // steps: only A's step; B's step is filtered out.
            List<UUID> visibleSteps = readUuidColumn(conn,
                    "SELECT id FROM public.approval_steps");
            assertThat(visibleSteps)
                    .as("runtime bound to tenant A must see ONLY A's approval_steps")
                    .contains(a.stepId())
                    .doesNotContain(b.stepId());

            // decisions: only A's decision; B's is filtered out.
            List<UUID> visibleDecisions = readUuidColumn(conn,
                    "SELECT id FROM public.approval_decisions");
            assertThat(visibleDecisions)
                    .as("runtime bound to tenant A must see ONLY A's approval_decisions")
                    .contains(a.decisionId())
                    .doesNotContain(b.decisionId());

            return null;
        });
    }

    /**
     * Belt-and-braces: with NO {@code app.tenant_id} bound (the production GUC
     * failure mode), the runtime role sees ZERO approval rows — never another
     * tenant's data — thanks to the blank-safe NULLIF policy (changelog 031).
     */
    @Test
    void runtimeRoleWithUnboundGucSeesZeroApprovalRows() {
        UUID tenantA = newSeededTenantId();
        seedApprovalGraph(tenantA);

        JdbcTemplate runtime = connectAs("grading_runtime", "grading_runtime_pwd");

        runtime.execute((Connection conn) -> {
            // Explicitly empty GUC == the prod "custom GUC reverted to ''" state.
            try (Statement st = conn.createStatement()) {
                st.execute("SET app.tenant_id = ''");
            }
            assertThat(countOnConnection(conn, "approval_requests"))
                    .as("unbound/empty app.tenant_id must yield zero approval_requests, never 22P02")
                    .isZero();
            assertThat(countOnConnection(conn, "approval_steps")).isZero();
            assertThat(countOnConnection(conn, "approval_decisions")).isZero();
            return null;
        });
    }

    // ------------------------------------------------------------------ //
    // Fixtures + helpers                                                  //
    // ------------------------------------------------------------------ //

    /** Seeds project + request + step + decision for one tenant (via superuser). */
    private ApprovalGraph seedApprovalGraph(UUID tenantId) {
        UUID projectId = UUID.randomUUID();
        adminJdbc.update(
                "INSERT INTO public.projects (id, tenant_id, code, name_i18n, status, version) "
                        + "VALUES (?, ?, ?, '{\"ru-RU\":\"P\"}'::jsonb, 'ACTIVE', 0)",
                projectId, tenantId, "PRJ-" + projectId.toString().substring(0, 8));

        UUID requestId = UUID.randomUUID();
        adminJdbc.update(
                "INSERT INTO public.approval_requests (id, tenant_id, project_id, entity_type, "
                        + "entity_id, requested_by, requested_at, current_status, version) "
                        + "VALUES (?, ?, ?, 'JOB_PROFILE', ?, ?, now(), 'PENDING', 0)",
                requestId, tenantId, projectId, UUID.randomUUID(), UUID.randomUUID());

        UUID stepId = UUID.randomUUID();
        adminJdbc.update(
                "INSERT INTO public.approval_steps (id, tenant_id, approval_request_id, step_order, "
                        + "approver_user_id, required_permission, status, version) "
                        + "VALUES (?, ?, ?, 1, ?, 'JOB_PROFILE_APPROVE', 'PENDING', 0)",
                stepId, tenantId, requestId, UUID.randomUUID());

        UUID decisionId = UUID.randomUUID();
        adminJdbc.update(
                "INSERT INTO public.approval_decisions (id, tenant_id, approval_step_id, "
                        + "decision_type, decided_by, decided_at, version) "
                        + "VALUES (?, ?, ?, 'APPROVE', ?, now(), 0)",
                decisionId, tenantId, stepId, UUID.randomUUID());

        return new ApprovalGraph(requestId, stepId, decisionId);
    }

    private long countAsAdmin(String table) {
        Long n = adminJdbc.queryForObject(
                "SELECT count(*) FROM public." + table, Long.class);
        return n == null ? -1 : n;
    }

    private static long countOnConnection(Connection conn, String table) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM public." + table)) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    private static List<UUID> readUuidColumn(Connection conn, String sql) throws SQLException {
        List<UUID> out = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getObject(1, UUID.class));
            }
        }
        return out;
    }

    private JdbcTemplate connectAs(String username, String password) {
        String url = POSTGRES.getJdbcUrl();
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(2);
        ds.setPoolName("approval-rls-" + username);
        openSources.add(ds);
        return new JdbcTemplate(ds);
    }

    private record ApprovalGraph(UUID requestId, UUID stepId, UUID decisionId) {
    }
}
