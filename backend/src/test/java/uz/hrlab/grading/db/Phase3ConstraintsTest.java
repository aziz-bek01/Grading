package uz.hrlab.grading.db;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import uz.hrlab.grading.AbstractIntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Phase 3 security/QA review remediation constraints
 * (changelog {@code tenant-schema/008-phase3-constraints.yaml}).
 *
 * <p>Verifies the DB-level defense-in-depth controls added to remediate:
 * <ul>
 *   <li><b>F-302 / D-315</b> — composite FK on
 *       {@code job_profiles (tenant_id, project_id, position_id)} →
 *       {@code positions (tenant_id, project_id, id)}.</li>
 *   <li><b>F-302</b> — same composite FK on
 *       {@code job_analysis_questionnaires.position_id}.</li>
 *   <li><b>F-302 (extra)</b> — composite FK on
 *       {@code job_analysis_answers (tenant_id, questionnaire_id)} →
 *       {@code job_analysis_questionnaires (tenant_id, id)}.</li>
 *   <li><b>F-304</b> — verifies prerequisite UNIQUE on
 *       {@code positions (tenant_id, project_id, id)}.</li>
 * </ul>
 *
 * <p>All probes go directly through JDBC (bypassing JPA / use cases) so we
 * are testing the database engine — not the application layer.
 */
@Tag("integration")
@Tag("db-constraints")
class Phase3ConstraintsTest extends AbstractIntegrationTest {

    // --------------------------------------------------- F-302 / D-315
    @Test
    void jobProfileWithPositionFromDifferentProjectIsRejected() {
        UUID tenant = newSeededTenantId();
        UUID projectA = createProject(tenant, "P3-JP-A", "ACTIVE");
        UUID projectB = createProject(tenant, "P3-JP-B", "ACTIVE");
        UUID deptB = createDepartment(tenant, projectB, "P3-DB", null);
        UUID positionInB = createPosition(tenant, projectB, deptB, "P3-POS-B");

        // Try to create a job_profile carrying projectA's project_id but pointing
        // at positionInB (which lives in projectB). The composite FK on
        // (tenant_id, project_id, position_id) → positions(tenant_id, project_id, id)
        // must reject this row.
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO job_profiles (id, tenant_id, project_id, position_id, "
                                + "status, revision_number, version) "
                                + "VALUES (?, ?, ?, ?, 'DRAFT', 1, 0)",
                        UUID.randomUUID(), tenant, projectA, positionInB))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_job_profiles_position");
    }

    @Test
    void jobProfileWithPositionInSameProjectSucceeds() {
        UUID tenant = newSeededTenantId();
        UUID projectA = createProject(tenant, "P3-JP-SP-A", "ACTIVE");
        UUID deptA = createDepartment(tenant, projectA, "P3-DA", null);
        UUID positionInA = createPosition(tenant, projectA, deptA, "P3-POS-A");

        UUID profileId = UUID.randomUUID();
        assertThatCode(() ->
                jdbcTemplate.update(
                        "INSERT INTO job_profiles (id, tenant_id, project_id, position_id, "
                                + "status, revision_number, version) "
                                + "VALUES (?, ?, ?, ?, 'DRAFT', 1, 0)",
                        profileId, tenant, projectA, positionInA))
                .doesNotThrowAnyException();

        // Sanity: row landed.
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM job_profiles WHERE id = ?",
                Integer.class, profileId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void jobProfileWithMismatchedTenantIdIsRejected() {
        // Same project_id and position_id but different tenant_id —
        // composite FK forces the entire (tenant, project, position) tuple
        // to align.
        UUID tenantA = newSeededTenantId();
        UUID tenantB = newSeededTenantId();
        UUID projectA = createProject(tenantA, "P3-JP-T-A", "ACTIVE");
        UUID deptA = createDepartment(tenantA, projectA, "P3-DT-A", null);
        UUID positionInA = createPosition(tenantA, projectA, deptA, "P3-POS-T-A");

        // tenantB claiming tenantA's position — composite FK denies it.
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO job_profiles (id, tenant_id, project_id, position_id, "
                                + "status, revision_number, version) "
                                + "VALUES (?, ?, ?, ?, 'DRAFT', 1, 0)",
                        UUID.randomUUID(), tenantB, projectA, positionInA))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_job_profiles_position");
    }

    // --------------------------------------------------- F-302 (questionnaire)
    @Test
    void questionnaireWithPositionFromDifferentProjectIsRejected() {
        UUID tenant = newSeededTenantId();
        UUID projectA = createProject(tenant, "P3-Q-A", "ACTIVE");
        UUID projectB = createProject(tenant, "P3-Q-B", "ACTIVE");
        UUID deptB = createDepartment(tenant, projectB, "P3-QD-B", null);
        UUID positionInB = createPosition(tenant, projectB, deptB, "P3-QPOS-B");

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO job_analysis_questionnaires (id, tenant_id, project_id, "
                                + "position_id, template_code, name_i18n, status, "
                                + "questionnaire_version, questions, version) "
                                + "VALUES (?, ?, ?, ?, 'TPL', '{\"ru-RU\":\"Q\"}'::jsonb, "
                                + "'DRAFT', 1, '[]'::jsonb, 0)",
                        UUID.randomUUID(), tenant, projectA, positionInB))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_jaq_position");
    }

    // --------------------------------------------------- F-302 (answer)
    @Test
    void answerReferencingQuestionnaireFromDifferentTenantIsRejected() {
        // questionnaire belongs to tenantA; try to insert an answer carrying
        // tenantB referencing it. Composite (tenant_id, questionnaire_id)
        // FK must reject.
        UUID tenantA = newSeededTenantId();
        UUID tenantB = newSeededTenantId();
        UUID projectA = createProject(tenantA, "P3-A-A", "ACTIVE");
        UUID deptA = createDepartment(tenantA, projectA, "P3-AD-A", null);
        UUID positionA = createPosition(tenantA, projectA, deptA, "P3-APOS-A");
        UUID questionnaireA = createQuestionnaire(tenantA, projectA, positionA);

        // tenantB needs a project/position so the answer row's own non-questionnaire
        // FKs (project_id on answers is optional — answer row has no FK on project_id
        // beyond tenant; we still need the project to exist for the FK on its own
        // table). Actually job_analysis_answers does not declare an FK on project_id
        // (verified in 006-create-job-analysis.yaml), so we can use any UUID for
        // project_id. But composite FK on (tenant_id, questionnaire_id) must fail.
        UUID projectB = createProject(tenantB, "P3-A-B", "ACTIVE");

        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO job_analysis_answers (id, tenant_id, project_id, "
                                + "questionnaire_id, question_id, answer_text, "
                                + "respondent_user_id, version) "
                                + "VALUES (?, ?, ?, ?, ?, 'x', ?, 0)",
                        UUID.randomUUID(), tenantB, projectB, questionnaireA,
                        UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_jaa_questionnaire");
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

    private UUID createPosition(UUID tenantId, UUID projectId, UUID departmentId, String code) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO positions (id, tenant_id, project_id, department_id, code, "
                        + "title_i18n, status, version) "
                        + "VALUES (?, ?, ?, ?, ?, '{\"ru-RU\":\"" + code + "\"}'::jsonb, "
                        + "'ACTIVE', 0)",
                id, tenantId, projectId, departmentId, code);
        return id;
    }

    private UUID createQuestionnaire(UUID tenantId, UUID projectId, UUID positionId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO job_analysis_questionnaires (id, tenant_id, project_id, "
                        + "position_id, template_code, name_i18n, status, "
                        + "questionnaire_version, questions, version) "
                        + "VALUES (?, ?, ?, ?, 'TPL', '{\"ru-RU\":\"Q\"}'::jsonb, "
                        + "'DRAFT', 1, '[]'::jsonb, 0)",
                id, tenantId, projectId, positionId);
        return id;
    }
}
