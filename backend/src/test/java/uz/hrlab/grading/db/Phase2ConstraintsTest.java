package uz.hrlab.grading.db;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import uz.hrlab.grading.AbstractIntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Phase 2 security review remediation constraints
 * (changelog {@code tenant-schema/004-phase2-constraints.yaml}).
 *
 * <p>Verifies the four DB-level defense-in-depth controls:
 * <ul>
 *   <li><b>F-203</b> — tenant_id FK to {@code public.tenants(id)} rejects
 *       inserts whose tenant is unknown.</li>
 *   <li><b>F-204</b> — composite FK on positions denies cross-project
 *       department references.</li>
 *   <li><b>F-206</b> — {@code prevent_department_cycle()} trigger rejects
 *       multi-step parent cycles (A→B, then try B→A).</li>
 *   <li><b>F-209</b> — {@code enforce_project_lock_immutability()} trigger
 *       rejects LOCKED → DRAFT/ACTIVE transitions.</li>
 * </ul>
 *
 * <p>All probes go directly through JDBC (bypassing JPA / use cases) so we
 * are testing the database engine — not the application layer.
 */
@Tag("integration")
@Tag("db-constraints")
class Phase2ConstraintsTest extends AbstractIntegrationTest {

    // --------------------------------------------------------------- F-203
    @Test
    void f203_insertProjectWithUnknownTenantIdFails() {
        UUID unknownTenant = UUID.randomUUID(); // NOT seeded into public.tenants

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO projects (id, tenant_id, code, name_i18n, status, version) "
                                + "VALUES (?, ?, 'F203-X', '{\"ru-RU\":\"X\"}'::jsonb, 'ACTIVE', 0)",
                        UUID.randomUUID(), unknownTenant))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_projects_tenant");
    }

    @Test
    void f203_insertDepartmentWithUnknownTenantIdFails() {
        UUID tenant = newSeededTenantId();
        UUID projectId = createProject(tenant, "F203-D-P", "ACTIVE");
        UUID unknownTenant = UUID.randomUUID();

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO departments (id, tenant_id, project_id, code, name_i18n, "
                                + "type, status, version) "
                                + "VALUES (?, ?, ?, 'F203-D', '{\"ru-RU\":\"D\"}'::jsonb, "
                                + "'DEPARTMENT', 'ACTIVE', 0)",
                        UUID.randomUUID(), unknownTenant, projectId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_departments_tenant");
    }

    @Test
    void f203_insertPositionWithUnknownTenantIdFails() {
        UUID tenant = newSeededTenantId();
        UUID projectId = createProject(tenant, "F203-P-P", "ACTIVE");
        UUID deptId = createDepartment(tenant, projectId, "F203-P-D", null);
        UUID unknownTenant = UUID.randomUUID();

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO positions (id, tenant_id, project_id, department_id, code, "
                                + "title_i18n, status, version) "
                                + "VALUES (?, ?, ?, ?, 'F203-P', '{\"ru-RU\":\"P\"}'::jsonb, "
                                + "'ACTIVE', 0)",
                        UUID.randomUUID(), unknownTenant, projectId, deptId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_positions_tenant");
    }

    // --------------------------------------------------------------- F-204
    @Test
    void f204_positionWithDepartmentFromDifferentProjectFails() {
        UUID tenant = newSeededTenantId();
        UUID projectA = createProject(tenant, "F204-A", "ACTIVE");
        UUID projectB = createProject(tenant, "F204-B", "ACTIVE");
        UUID deptInA = createDepartment(tenant, projectA, "F204-DA", null);

        // Try to create a position in project B referencing a department in project A.
        // The composite FK on (project_id, department_id) → departments(project_id, id)
        // must reject this row.
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO positions (id, tenant_id, project_id, department_id, code, "
                                + "title_i18n, status, version) "
                                + "VALUES (?, ?, ?, ?, 'F204-POS', '{\"ru-RU\":\"X\"}'::jsonb, "
                                + "'ACTIVE', 0)",
                        UUID.randomUUID(), tenant, projectB, deptInA))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_positions_department");
    }

    // --------------------------------------------------------------- F-206
    @Test
    void f206_directParentCycleIsRejectedByTrigger() {
        UUID tenant = newSeededTenantId();
        UUID projectId = createProject(tenant, "F206-P", "ACTIVE");
        UUID deptA = createDepartment(tenant, projectId, "A", null);
        UUID deptB = createDepartment(tenant, projectId, "B", deptA);

        // B is a child of A. Now try to make A a child of B — that creates A→B→A.
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "UPDATE departments SET parent_id = ? WHERE id = ?",
                        deptB, deptA))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("cycle");
    }

    // --------------------------------------------------------------- F-209
    @Test
    void f209_lockedProjectCannotBeReverted() {
        UUID tenant = newSeededTenantId();
        UUID projectId = createProject(tenant, "F209-P", "ACTIVE");

        // Lock the project — allowed.
        int rowsLocked = jdbcTemplate.update(
                "UPDATE projects SET status = 'LOCKED' WHERE id = ?", projectId);
        assertThat(rowsLocked).isEqualTo(1);

        // Now try to revert to ACTIVE — trigger must reject.
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "UPDATE projects SET status = 'ACTIVE' WHERE id = ?", projectId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("LOCKED");

        // Same for DRAFT.
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "UPDATE projects SET status = 'DRAFT' WHERE id = ?", projectId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("LOCKED");

        // ARCHIVED is allowed (still a one-way step).
        int rowsArchived = jdbcTemplate.update(
                "UPDATE projects SET status = 'ARCHIVED' WHERE id = ?", projectId);
        assertThat(rowsArchived).isEqualTo(1);
    }

    // ------------------------------------------------------------- helpers

    private UUID createProject(UUID tenantId, String code, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO projects (id, tenant_id, code, name_i18n, status, version) "
                        + "VALUES (?, ?, ?, '{\"ru-RU\":\"" + code + "\"}'::jsonb, ?, 0)",
                id, tenantId, code, status);
        return id;
    }

    private UUID createDepartment(UUID tenantId, UUID projectId, String code, UUID parentId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO departments (id, tenant_id, project_id, parent_id, code, name_i18n, "
                        + "type, status, version) "
                        + "VALUES (?, ?, ?, ?, ?, '{\"ru-RU\":\"" + code + "\"}'::jsonb, "
                        + "'DEPARTMENT', 'ACTIVE', 0)",
                id, tenantId, projectId, parentId, code);
        return id;
    }
}
