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
import uz.hrlab.grading.methodology.application.CreateMethodologyCommand;
import uz.hrlab.grading.methodology.application.CreateMethodologyFromScratchUseCase;
import uz.hrlab.grading.methodology.application.FactorCommand;
import uz.hrlab.grading.methodology.application.FactorLevelCommand;
import uz.hrlab.grading.methodology.application.FactorLevelService;
import uz.hrlab.grading.methodology.application.FactorService;
import uz.hrlab.grading.methodology.application.MethodologyAggregate;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.organization.domain.DepartmentStatus;
import uz.hrlab.grading.organization.domain.DepartmentType;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
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

/**
 * Feature 1 (Docker-gated) — the CORE bug-fix proof for
 * {@code GET /api/v1/evaluations/my} ({@link EvaluationQueries#listMine()}).
 *
 * <p>A committee-member principal ({@code EVALUATION_COMMITTEE_MEMBER}, a
 * department-scoped role) whose {@code user_department_scopes} is EMPTY is
 * fail-closed for the GENERAL {@code list()} read — they would see ZERO rows. But
 * {@code listMine()} is self-scoped ({@code tenant_id} + {@code evaluator_user_id}
 * both pinned server-side) and DELIBERATELY bypasses that department filter, so
 * the member still sees their OWN assigned sheets.
 *
 * <p>This test proves:
 * <ol>
 *   <li>the empty-scope committee member sees their OWN evaluation via
 *       {@code listMine()} (the bug was: zero rows);</li>
 *   <li>they NEVER see another user's row in the same tenant;</li>
 *   <li>they NEVER see another tenant's row (cross-tenant isolation);</li>
 *   <li>the general {@code list()} read DOES return zero rows for the same
 *       empty-scope member — confirming {@code listMine()} is the intentional,
 *       narrow carve-out and the department filter is otherwise intact.</li>
 * </ol>
 */
@Tag("integration")
@Tag("tenant-isolation")
class MyEvaluationsScopeBypassIntegrationTest extends AbstractIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired DepartmentRepository departments;
    @Autowired PositionRepository positions;
    @Autowired CreateMethodologyFromScratchUseCase createMethodologyUseCase;
    @Autowired FactorService factorService;
    @Autowired FactorLevelService factorLevelService;
    @Autowired ApproveMethodologyVersionUseCase approveMethodologyUseCase;
    @Autowired CreateEvaluationUseCase createEvaluationUseCase;
    @Autowired EvaluationQueries queries;

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    void emptyScopeCommitteeMemberSeesOwnRowsButNeverOthersOrOtherTenants() {
        Seed a = seed("MEA");
        // Two distinct evaluator users in tenant A; one is "me" (the committee member).
        UUID me = seedUser(UUID.randomUUID());
        UUID peer = seedUser(UUID.randomUUID());

        // Create one evaluation owned by ME and one owned by a PEER (PM context can
        // assign an explicit evaluator_user_id). Distinct positions so the
        // one-active-per-(position, version) guard is satisfied.
        UUID mineEval = createEvaluationAs(a, a.positionId, me);
        UUID peerEval = createEvaluationAs(a, secondPosition(a), peer);

        // Tenant B with its own evaluation owned by a different user.
        Seed b = seed("MEB");
        UUID bUser = seedUser(UUID.randomUUID());
        UUID bEval = createEvaluationAs(b, b.positionId, bUser);

        // ---- committee member context: dept-scoped role, EMPTY department scope ----
        TenantContextHolder.set(new TenantContext(
                me, a.tenantId, Set.of(a.projectId),
                Set.of("EVALUATION_COMMITTEE_MEMBER"),
                Set.of("EVALUATION_READ"),
                Set.of(), false, "ru-RU"));

        List<MyEvaluationRow> mine = queries.listMine();

        // (1) sees own row; (2)+(3) never a peer's nor another tenant's.
        assertThat(mine).extracting(MyEvaluationRow::evaluationId)
                .containsExactly(mineEval)
                .doesNotContain(peerEval, bEval);

        // (4) the GENERAL list read is fail-closed (zero rows) for the SAME empty-
        //     scope member — proving listMine() is the intentional narrow bypass.
        var general = queries.list(null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 50));
        assertThat(general.getContent())
                .as("general list is department-scope fail-closed for an empty-scope member")
                .isEmpty();
    }

    // --------------------------------------------------------------- helpers

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
                        UUID methodologyVersionId, UUID actorId) { }

    private Seed seed(String codePrefix) {
        UUID tenant = newSeededTenantId();
        UUID actor = seedUser(UUID.randomUUID());
        ProjectJpaEntity proj = projects.save(new ProjectJpaEntity(
                UUID.randomUUID(), tenant, codePrefix + "-PR", Map.of("ru-RU", codePrefix),
                null, ProjectStatus.ACTIVE, null, null, null));
        DepartmentJpaEntity dpt = departments.save(new DepartmentJpaEntity(
                UUID.randomUUID(), tenant, proj.getId(), null, codePrefix + "-DPT",
                Map.of("ru-RU", codePrefix), DepartmentType.DEPARTMENT, DepartmentStatus.ACTIVE));
        PositionJpaEntity pos = positions.save(new PositionJpaEntity(
                UUID.randomUUID(), tenant, proj.getId(), dpt.getId(), codePrefix + "-POS",
                Map.of("ru-RU", codePrefix), null, null, null, null, PositionStatus.ACTIVE));

        TenantContextHolder.set(new TenantContext(
                actor, tenant, Set.of(proj.getId()),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("METHODOLOGY_READ", "METHODOLOGY_CREATE", "METHODOLOGY_EDIT",
                        "METHODOLOGY_APPROVE", "METHODOLOGY_LOCK"),
                Set.of(), false, "ru-RU"));

        MethodologyAggregate agg = createMethodologyUseCase.create(new CreateMethodologyCommand(
                proj.getId(), codePrefix + "-MTH",
                Map.of("ru-RU", codePrefix), Map.of("ru-RU", "desc"),
                MethodologyType.CUSTOM, ScoringMode.DIRECT_POINTS, new BigDecimal("1000")));
        UUID versionId = agg.currentVersion().id();
        Factor f1 = factorService.add(versionId, new FactorCommand(
                codePrefix + "-F1", Map.of("ru-RU", "F1"), null,
                new BigDecimal("500"), new BigDecimal("500"), 1, true));
        factorLevelService.add(f1.id(), new FactorLevelCommand(
                "L1", 1, new BigDecimal("100"), null, Map.of("ru-RU", "L1"), null));
        approveMethodologyUseCase.approve(versionId);

        TenantContextHolder.clear();
        return new Seed(tenant, proj.getId(), dpt.getId(), pos.getId(), versionId, actor);
    }
}
