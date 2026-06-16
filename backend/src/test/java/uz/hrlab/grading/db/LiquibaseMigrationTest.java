package uz.hrlab.grading.db;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import uz.hrlab.grading.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies all Liquibase changesets and seeds run cleanly. */
@Tag("integration")
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

    // --- BE-1: METHODOLOGY_EDIT_APPROVED catalogue + super-admin-only grant ---

    @Test
    void methodologyEditApprovedPermissionSeeded() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.permissions WHERE code = 'METHODOLOGY_EDIT_APPROVED'",
                Long.class);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void methodologyEditApprovedHeldExactlyByHrlabSuperAdmin() {
        // The only role holding METHODOLOGY_EDIT_APPROVED must be HRLAB_SUPER_ADMIN.
        var holders = jdbc.queryForList(
                "SELECT r.code FROM public.role_permissions rp "
                        + "JOIN public.permissions p ON p.id = rp.permission_id "
                        + "JOIN public.roles r ON r.id = rp.role_id "
                        + "WHERE p.code = 'METHODOLOGY_EDIT_APPROVED' ORDER BY r.code",
                String.class);
        assertThat(holders).containsExactly("HRLAB_SUPER_ADMIN");
    }

    @Test
    void noNonSuperAdminRoleHoldsMethodologyEditApproved() {
        Long extra = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.role_permissions rp "
                        + "JOIN public.permissions p ON p.id = rp.permission_id "
                        + "JOIN public.roles r ON r.id = rp.role_id "
                        + "WHERE p.code = 'METHODOLOGY_EDIT_APPROVED' "
                        + "AND r.code <> 'HRLAB_SUPER_ADMIN'",
                Long.class);
        assertThat(extra).isZero();
    }

    // --- TASK 2: lock in super-admin's workflow/approval grant ---

    /**
     * Regression guard for the reported "super adminga hamma narsa berkilib
     * qolibdi" lockout. After ALL seeds run (seeds/005 last, see
     * db.changelog-master.yaml), HRLAB_SUPER_ADMIN must hold the workflow +
     * approval decision permissions. This pins them so a forward-only seed
     * regression (or a botched carve-out list) is caught at migration time.
     */
    @Test
    void superAdminHoldsWorkflowAndApprovalPermissions() {
        var granted = jdbc.queryForList(
                "SELECT p.code FROM public.role_permissions rp "
                        + "JOIN public.permissions p ON p.id = rp.permission_id "
                        + "JOIN public.roles r ON r.id = rp.role_id "
                        + "WHERE r.code = 'HRLAB_SUPER_ADMIN' "
                        + "AND p.code IN ('APPROVAL_REQUEST_DECIDE','APPROVAL_REQUEST_CREATE',"
                        + "'APPROVAL_REQUEST_CANCEL','WORKFLOW_EDIT')",
                String.class);
        assertThat(granted).containsExactlyInAnyOrder(
                "APPROVAL_REQUEST_DECIDE", "APPROVAL_REQUEST_CREATE",
                "APPROVAL_REQUEST_CANCEL", "WORKFLOW_EDIT");
    }

    // --- P2-FIX (Task 1): CLIENT_* catalogue codes + grants ---

    @Test
    void clientCompanyPermissionsSeeded() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.permissions WHERE code IN ("
                        + "'CLIENT_LIST','CLIENT_VIEW','CLIENT_UPDATE')",
                Long.class);
        assertThat(count).isEqualTo(3L);
    }

    @Test
    void clientCompanyAdminHoldsClientUpdate() {
        // The reported fail-closed defect: CLIENT_COMPANY_ADMIN must be able to
        // PATCH its own client profile, i.e. hold CLIENT_UPDATE.
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.role_permissions rp "
                        + "JOIN public.permissions p ON p.id = rp.permission_id "
                        + "JOIN public.roles r ON r.id = rp.role_id "
                        + "WHERE r.code = 'CLIENT_COMPANY_ADMIN' AND p.code = 'CLIENT_UPDATE'",
                Long.class);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void superAdminHoldsClientView() {
        // CLIENT_* are non-carve-out codes, so the seeds/005 backfill (which runs
        // AFTER seeds/007) must grant them to HRLAB_SUPER_ADMIN.
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.role_permissions rp "
                        + "JOIN public.permissions p ON p.id = rp.permission_id "
                        + "JOIN public.roles r ON r.id = rp.role_id "
                        + "WHERE r.code = 'HRLAB_SUPER_ADMIN' AND p.code = 'CLIENT_VIEW'",
                Long.class);
        assertThat(count).isEqualTo(1L);
    }

    // --- my-evaluations inbox finders are index-backed (changesets 015 / 034) ---

    @Test
    void myEvaluationsInboxIndexesExist() {
        // (tenant_id, evaluator_user_id) — backs the my-evaluations list finder.
        assertThat(indexExists("idx_evaluations_tenant_evaluator")).isTrue();
        // (tenant_id, evaluator_user_id, assignment_status) — backs the panel
        // assignment inbox finder.
        assertThat(indexExists("idx_panel_assignments_evaluator")).isTrue();
    }

    private boolean indexExists(String name) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes "
                        + "WHERE schemaname='public' AND indexname=?",
                Long.class, name);
        return count != null && count == 1L;
    }

    private boolean tableExists(String name) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema='public' AND table_name=?",
                Long.class, name);
        return count != null && count == 1L;
    }
}
