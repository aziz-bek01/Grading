package uz.hrlab.grading.evaluation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.evaluation.api.MyEvaluationRow;
import uz.hrlab.grading.evaluation.application.CreateEvaluationCommand;
import uz.hrlab.grading.evaluation.application.CreateEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.EvaluationQueries;
import uz.hrlab.grading.methodology.application.ApproveMethodologyVersionUseCase;
import uz.hrlab.grading.methodology.application.ArchiveMethodologyUseCase;
import uz.hrlab.grading.methodology.application.CreateMethodologyCommand;
import uz.hrlab.grading.methodology.application.CreateMethodologyFromScratchUseCase;
import uz.hrlab.grading.methodology.application.FactorCommand;
import uz.hrlab.grading.methodology.application.FactorLevelCommand;
import uz.hrlab.grading.methodology.application.FactorLevelService;
import uz.hrlab.grading.methodology.application.FactorService;
import uz.hrlab.grading.methodology.application.MethodologyAggregate;
import uz.hrlab.grading.methodology.application.MethodologyQueries;
import uz.hrlab.grading.methodology.application.RestoreMethodologyUseCase;
import uz.hrlab.grading.methodology.api.MethodologyResponse;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.Methodology;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.organization.domain.DepartmentStatus;
import uz.hrlab.grading.organization.domain.DepartmentType;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.project.domain.ProjectStatus;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Docker-gated integration coverage for the 4-part evaluation feature backend:
 * <ul>
 *   <li>PART A — {@code listMine()} EXCLUDES sheets of an ARCHIVED methodology
 *       and populates the four new {@link MyEvaluationRow} methodology fields.</li>
 *   <li>PART B — {@code MethodologyQueries.findMyMethodologiesInProject} returns
 *       only ACTIVE methodologies the caller has an own evaluation under.</li>
 *   <li>PART C — {@link RestoreMethodologyUseCase}: happy path, not-archived
 *       guard, cross-tenant 404.</li>
 *   <li>PART D — {@code MethodologyQueries.countInProgressEvaluations} counts
 *       DRAFT/INCOMPLETE/COMPLETE only.</li>
 * </ul>
 */
@Tag("integration")
@Tag("tenant-isolation")
class MyEvaluationsActiveMethodologyIntegrationTest extends AbstractIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired DepartmentRepository departments;
    @Autowired PositionRepository positions;
    @Autowired CreateMethodologyFromScratchUseCase createMethodologyUseCase;
    @Autowired FactorService factorService;
    @Autowired FactorLevelService factorLevelService;
    @Autowired ApproveMethodologyVersionUseCase approveMethodologyUseCase;
    @Autowired ArchiveMethodologyUseCase archiveMethodologyUseCase;
    @Autowired RestoreMethodologyUseCase restoreMethodologyUseCase;
    @Autowired CreateEvaluationUseCase createEvaluationUseCase;
    @Autowired EvaluationQueries evaluationQueries;
    @Autowired MethodologyQueries methodologyQueries;

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    // =================================================================== PART A

    @Test
    void listMineExcludesArchivedMethodologyAndCarriesMethodologyFields() {
        Seed active = seed("AMA", "active");
        Seed archived = seed("AMB", "arch");
        UUID me = seedUser(UUID.randomUUID());

        // One sheet under each methodology, both owned by me.
        UUID activeEval = createEvaluationAs(active, active.positionId, me);
        UUID archivedEval = createEvaluationAs(archived, archived.positionId, me);

        // Archive the second methodology container (its version becomes invisible to listMine).
        archiveAs(archived);

        actAs(me, active.tenantId, active.projectId);
        List<MyEvaluationRow> mine = evaluationQueries.listMine();

        // (1) only the ACTIVE-methodology sheet appears; the archived one is dropped.
        assertThat(mine).extracting(MyEvaluationRow::evaluationId)
                .containsExactly(activeEval)
                .doesNotContain(archivedEval);

        // (2) the four new methodology fields are populated for the surviving row.
        MyEvaluationRow row = mine.get(0);
        assertThat(row.methodologyVersionId()).isEqualTo(active.methodologyVersionId);
        assertThat(row.methodologyId()).isEqualTo(active.methodologyId);
        assertThat(row.methodologyName()).containsEntry("ru-RU", "AMA");
        assertThat(row.methodologyActiveVersionNumber()).isEqualTo(1);
    }

    // =================================================================== PART B

    @Test
    void findMyMethodologiesInProjectReturnsOnlyAssignedAndActive() {
        Seed assigned = seed("MBA", "assigned");
        // A second ACTIVE methodology in the SAME project that the caller is NOT
        // assigned to (no own evaluation) — must NOT appear.
        Seed unassigned = seedInProject("MBU", assigned.tenantId, assigned.projectId);
        // An ARCHIVED methodology the caller IS assigned to — must NOT appear.
        Seed archived = seedInProject("MBR", assigned.tenantId, assigned.projectId);

        UUID me = seedUser(UUID.randomUUID());
        createEvaluationAs(assigned, assigned.positionId, me);
        createEvaluationAs(archived, archived.positionId, me);
        archiveAs(archived);

        actAs(me, assigned.tenantId, assigned.projectId);
        List<MethodologyResponse> mine =
                methodologyQueries.findMyMethodologiesInProject(assigned.projectId);

        assertThat(mine).extracting(MethodologyResponse::id)
                .containsExactly(assigned.methodologyId)
                .doesNotContain(unassigned.methodologyId, archived.methodologyId);
    }

    // =================================================================== PART C

    @Test
    void restoreHappyPathFlipsArchivedToActive() {
        Seed s = seed("MCA", "restore");
        archiveAs(s);

        actAsEditor(s);
        Methodology restored = restoreMethodologyUseCase.restore(s.methodologyId, "reinstate");
        assertThat(restored.status()).isEqualTo(MethodologyStatus.ACTIVE);
    }

    @Test
    void restoreNonArchivedIsRejected() {
        Seed s = seed("MCB", "restore2"); // still ACTIVE
        actAsEditor(s);
        assertThatThrownBy(() ->
                restoreMethodologyUseCase.restore(s.methodologyId, "reinstate"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("restored");
    }

    @Test
    void restoreCrossTenantIsNotFound() {
        Seed a = seed("MCC", "restoreA");
        archiveAs(a);
        Seed b = seed("MCD", "restoreB"); // different tenant editor

        // Act as tenant B's editor but target tenant A's methodology id → 404.
        actAsEditor(b);
        assertThatThrownBy(() ->
                restoreMethodologyUseCase.restore(a.methodologyId, "reinstate"))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    // =================================================================== PART D

    @Test
    void inProgressCountCountsOnlyPreSubmissionEvaluations() {
        Seed s = seed("MDA", "count");
        UUID u1 = seedUser(UUID.randomUUID());
        UUID u2 = seedUser(UUID.randomUUID());
        // Two DRAFT evaluations under this methodology's single version.
        createEvaluationAs(s, s.positionId, u1);
        createEvaluationAs(s, secondPosition(s), u2);

        actAsEditor(s);
        long count = methodologyQueries.countInProgressEvaluations(s.methodologyId);
        assertThat(count).isEqualTo(2L);
    }

    // --------------------------------------------------------------- helpers

    private void actAs(UUID userId, UUID tenantId, UUID projectId) {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("EVALUATION_COMMITTEE_MEMBER"),
                Set.of("EVALUATION_READ"),
                Set.of(), false, "ru-RU"));
    }

    private void actAsEditor(Seed s) {
        TenantContextHolder.set(new TenantContext(
                s.actorId, s.tenantId, Set.of(s.projectId),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("METHODOLOGY_READ", "METHODOLOGY_EDIT"),
                Set.of(), false, "ru-RU"));
    }

    private void archiveAs(Seed s) {
        TenantContextHolder.set(new TenantContext(
                s.actorId, s.tenantId, Set.of(s.projectId),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("METHODOLOGY_EDIT"),
                Set.of(), false, "ru-RU"));
        archiveMethodologyUseCase.archive(s.methodologyId, "retire");
        TenantContextHolder.clear();
    }

    private UUID createEvaluationAs(Seed s, UUID positionId, UUID evaluatorUserId) {
        TenantContextHolder.set(new TenantContext(
                s.actorId, s.tenantId, Set.of(s.projectId),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("EVALUATION_READ", "EVALUATION_EDIT"),
                Set.of(), false, "ru-RU"));
        UUID id = createEvaluationUseCase.create(new CreateEvaluationCommand(
                positionId, s.methodologyVersionId, evaluatorUserId)).id();
        TenantContextHolder.clear();
        return id;
    }

    private UUID secondPosition(Seed s) {
        PositionJpaEntity pos = positions.save(new PositionJpaEntity(
                UUID.randomUUID(), s.tenantId, s.projectId, s.departmentId, "P2-" + UUID.randomUUID(),
                Map.of("ru-RU", "Pos2"), null, null, null, null, PositionStatus.ACTIVE));
        return pos.getId();
    }

    private record Seed(UUID tenantId, UUID projectId, UUID departmentId, UUID positionId,
                        UUID methodologyId, UUID methodologyVersionId, UUID actorId,
                        UUID factorId, UUID factorLevelId) { }

    /** Seed a fresh tenant + project + department + position + ACTIVE methodology. */
    private Seed seed(String codePrefix, String unused) {
        UUID tenant = newSeededTenantId();
        ProjectJpaEntity proj = projects.save(new ProjectJpaEntity(
                UUID.randomUUID(), tenant, codePrefix + "-PR", Map.of("ru-RU", codePrefix),
                null, ProjectStatus.ACTIVE, null, null, null));
        return seedInProject(codePrefix, tenant, proj.getId());
    }

    /** Seed a department + position + ACTIVE methodology inside an EXISTING project. */
    private Seed seedInProject(String codePrefix, UUID tenant, UUID projectId) {
        UUID actor = seedUser(UUID.randomUUID());
        DepartmentJpaEntity dpt = departments.save(new DepartmentJpaEntity(
                UUID.randomUUID(), tenant, projectId, null, codePrefix + "-DPT",
                Map.of("ru-RU", codePrefix), DepartmentType.DEPARTMENT, DepartmentStatus.ACTIVE));
        PositionJpaEntity pos = positions.save(new PositionJpaEntity(
                UUID.randomUUID(), tenant, projectId, dpt.getId(), codePrefix + "-POS",
                Map.of("ru-RU", codePrefix), null, null, null, null, PositionStatus.ACTIVE));

        TenantContextHolder.set(new TenantContext(
                actor, tenant, Set.of(projectId),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("METHODOLOGY_READ", "METHODOLOGY_CREATE", "METHODOLOGY_EDIT",
                        "METHODOLOGY_APPROVE", "METHODOLOGY_LOCK"),
                Set.of(), false, "ru-RU"));

        MethodologyAggregate agg = createMethodologyUseCase.create(new CreateMethodologyCommand(
                projectId, codePrefix + "-MTH",
                Map.of("ru-RU", codePrefix), Map.of("ru-RU", "desc"),
                MethodologyType.CUSTOM, ScoringMode.DIRECT_POINTS, new BigDecimal("1000")));
        UUID methodologyId = agg.methodology().id();
        UUID versionId = agg.currentVersion().id();
        Factor f1 = factorService.add(versionId, new FactorCommand(
                codePrefix + "-F1", Map.of("ru-RU", "F1"), null,
                new BigDecimal("500"), new BigDecimal("500"), 1, true));
        var l1 = factorLevelService.add(f1.id(), new FactorLevelCommand(
                "L1", 1, new BigDecimal("100"), null, Map.of("ru-RU", "L1"), null));
        factorLevelService.add(f1.id(), new FactorLevelCommand(
                "L2", 2, new BigDecimal("500"), null, Map.of("ru-RU", "L2"), null));
        approveMethodologyUseCase.approve(versionId);

        TenantContextHolder.clear();
        return new Seed(tenant, projectId, dpt.getId(), pos.getId(), methodologyId, versionId,
                actor, f1.id(), l1.id());
    }
}
