package uz.hrlab.grading.evaluation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.evaluation.api.BulkCreatePanelsResponse;
import uz.hrlab.grading.evaluation.api.MyEvaluationRow;
import uz.hrlab.grading.evaluation.application.BulkCreatePanelsCommand;
import uz.hrlab.grading.evaluation.application.BulkCreatePanelsUseCase;
import uz.hrlab.grading.evaluation.application.EvaluationQueries;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
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
 * Docker-gated END-TO-END regression for the "Open evaluation panel" wizard bug:
 * {@code POST /api/v1/panels/bulk-create} with {@code start_evaluations=true} must
 * not only create the per-position panels but ALSO roster-lock each fully-rostered
 * panel so the per-evaluator DRAFT Evaluation sheets exist — otherwise assigned
 * experts see NOTHING in {@code GET /api/v1/evaluations/my}
 * ({@link EvaluationQueries#listMine()}) and cannot score (the reported defect).
 *
 * <p>Exercises the REAL {@link BulkCreatePanelsUseCase} (with its self-invoked
 * {@code REQUIRES_NEW} create/assign/lock wrappers honoured by the Spring proxy)
 * against a real Postgres, and reads back through {@code listMine()} as each
 * assigned evaluator. Asserts:
 * <ol>
 *   <li>start_evaluations=true + a full mandatory trio → every created panel ends
 *       AWAITING_EVALUATIONS and each assigned evaluator's {@code listMine()}
 *       returns their DRAFT sheet (the bug fix);</li>
 *   <li>start_evaluations=false → UNCHANGED: panels stay COLLECTING, no DRAFT
 *       sheets, every assigned evaluator's {@code listMine()} is empty (contract);</li>
 *   <li>start_evaluations=true but the roster is missing a mandatory role →
 *       ROSTER_LOCK_FAILED, the panel stays COLLECTING, created still counts it,
 *       no sheets created.</li>
 * </ol>
 */
@Tag("integration")
@Tag("tenant-isolation")
class BulkCreatePanelsStartEvaluationsIntegrationTest extends AbstractIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired DepartmentRepository departments;
    @Autowired PositionRepository positions;
    @Autowired CreateMethodologyFromScratchUseCase createMethodologyUseCase;
    @Autowired FactorService factorService;
    @Autowired FactorLevelService factorLevelService;
    @Autowired ApproveMethodologyVersionUseCase approveMethodologyUseCase;

    @Autowired BulkCreatePanelsUseCase bulkCreateUseCase;
    @Autowired EvaluationQueries queries;
    @Autowired PanelRepository panels;
    @Autowired EvaluationRepository evaluations;

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    void startEvaluationsTrueLocksEachPanelAndAssignedEvaluatorsSeeTheirSheets() {
        Seed s = seed("BSE-A");
        UUID p1 = s.positionId;
        UUID p2 = secondPosition(s);

        // A full mandatory trio shared across both positions.
        UUID hr = seedUser(UUID.randomUUID());
        UUID dept = seedUser(UUID.randomUUID());
        UUID ext = seedUser(UUID.randomUUID());
        List<BulkCreatePanelsCommand.RosterSeat> roster = List.of(
                new BulkCreatePanelsCommand.RosterSeat(hr, EvaluatorRole.HR_DIRECTOR),
                new BulkCreatePanelsCommand.RosterSeat(dept, EvaluatorRole.DEPARTMENT_DIRECTOR),
                new BulkCreatePanelsCommand.RosterSeat(ext, EvaluatorRole.EXTERNAL_EXPERT));

        setManagerContext(s);
        BulkCreatePanelsResponse res = bulkCreateUseCase.execute(
                new BulkCreatePanelsCommand(s.methodologyVersionId, List.of(p1, p2), roster, true));

        // Both panels created and started; no failures.
        assertThat(res.created()).isEqualTo(2);
        assertThat(res.failed()).isEmpty();

        // Both panels transitioned COLLECTING -> AWAITING_EVALUATIONS.
        List<EvaluationPanelStatus> statuses = panels
                .findAllByTenantIdAndPositionId(s.tenantId, p1, org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent().stream().map(EvaluationPanelJpaEntity::getStatus).toList();
        assertThat(statuses).containsOnly(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        assertThat(panels.findAllByTenantIdAndPositionId(s.tenantId, p2,
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent()).allMatch(p -> p.getStatus() == EvaluationPanelStatus.AWAITING_EVALUATIONS);

        // One DRAFT Evaluation per seat per panel = 3 seats * 2 panels = 6 sheets.
        // Each assigned evaluator owns one sheet per panel — so listMine() returns 2.
        assertThat(myDraftSheets(s, hr)).hasSize(2);
        assertThat(myDraftSheets(s, dept)).hasSize(2);
        assertThat(myDraftSheets(s, ext)).hasSize(2);

        // The sheets are pinned to the right positions (one per panel).
        assertThat(myEvalRows(s, hr)).extracting(MyEvaluationRow::positionId)
                .containsExactlyInAnyOrder(p1, p2);
    }

    @Test
    void startEvaluationsFalseLeavesPanelsCollectingAndListMineEmpty() {
        Seed s = seed("BSE-B");
        UUID p1 = s.positionId;

        UUID hr = seedUser(UUID.randomUUID());
        UUID dept = seedUser(UUID.randomUUID());
        UUID ext = seedUser(UUID.randomUUID());
        List<BulkCreatePanelsCommand.RosterSeat> roster = List.of(
                new BulkCreatePanelsCommand.RosterSeat(hr, EvaluatorRole.HR_DIRECTOR),
                new BulkCreatePanelsCommand.RosterSeat(dept, EvaluatorRole.DEPARTMENT_DIRECTOR),
                new BulkCreatePanelsCommand.RosterSeat(ext, EvaluatorRole.EXTERNAL_EXPERT));

        setManagerContext(s);
        BulkCreatePanelsResponse res = bulkCreateUseCase.execute(
                new BulkCreatePanelsCommand(s.methodologyVersionId, List.of(p1), roster, false));

        assertThat(res.created()).isEqualTo(1);
        assertThat(res.failed()).isEmpty();

        // UNCHANGED contract: panel stays COLLECTING, no DRAFT sheets created.
        assertThat(panels.findAllByTenantIdAndPositionId(s.tenantId, p1,
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent()).allMatch(p -> p.getStatus() == EvaluationPanelStatus.COLLECTING);

        // Each assigned evaluator's "My Evaluations" is EMPTY until a manual lock.
        assertThat(myEvalRows(s, hr)).isEmpty();
        assertThat(myEvalRows(s, dept)).isEmpty();
        assertThat(myEvalRows(s, ext)).isEmpty();
    }

    @Test
    void startEvaluationsTrueWithMandatoryRoleMissingReportsLockFailedAndStaysCollecting() {
        Seed s = seed("BSE-C");
        UUID p1 = s.positionId;

        // Roster missing EXTERNAL_EXPERT (a mandatory role) — the seats assign fine
        // (no per-seat failure) but LockRosterUseCase rejects the incomplete trio.
        UUID hr = seedUser(UUID.randomUUID());
        UUID dept = seedUser(UUID.randomUUID());
        UUID extra = seedUser(UUID.randomUUID());
        List<BulkCreatePanelsCommand.RosterSeat> roster = List.of(
                new BulkCreatePanelsCommand.RosterSeat(hr, EvaluatorRole.HR_DIRECTOR),
                new BulkCreatePanelsCommand.RosterSeat(dept, EvaluatorRole.DEPARTMENT_DIRECTOR),
                new BulkCreatePanelsCommand.RosterSeat(extra, EvaluatorRole.ADDITIONAL));

        setManagerContext(s);
        BulkCreatePanelsResponse res = bulkCreateUseCase.execute(
                new BulkCreatePanelsCommand(s.methodologyVersionId, List.of(p1), roster, true));

        // Panel still created (counted), but the lock failed.
        assertThat(res.created()).isEqualTo(1);
        assertThat(res.failed()).hasSize(1);
        BulkCreatePanelsResponse.Failure f = res.failed().get(0);
        assertThat(f.positionId()).isEqualTo(p1);
        assertThat(f.errorCode()).isEqualTo("ROSTER_LOCK_FAILED");
        assertThat(f.message()).contains("MANDATORY_ROLES_MISSING");

        // The panel STILL exists in COLLECTING (never un-created); no DRAFT sheets.
        assertThat(panels.findAllByTenantIdAndPositionId(s.tenantId, p1,
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent()).allMatch(p -> p.getStatus() == EvaluationPanelStatus.COLLECTING);
        assertThat(myEvalRows(s, hr)).isEmpty();
        assertThat(myEvalRows(s, dept)).isEmpty();
        assertThat(myEvalRows(s, extra)).isEmpty();
    }

    // --------------------------------------------------------------- helpers

    /** Read "My Evaluations" as the given evaluator (EVALUATION_READ, self-scoped). */
    private List<MyEvaluationRow> myEvalRows(Seed s, UUID evaluatorUserId) {
        TenantContextHolder.set(new TenantContext(
                evaluatorUserId, s.tenantId, Set.of(s.projectId),
                Set.of("EVALUATION_COMMITTEE_MEMBER"),
                Set.of("EVALUATION_READ"),
                Set.of(), false, "ru-RU"));
        List<MyEvaluationRow> rows = queries.listMine();
        TenantContextHolder.clear();
        return rows;
    }

    private List<MyEvaluationRow> myDraftSheets(Seed s, UUID evaluatorUserId) {
        return myEvalRows(s, evaluatorUserId).stream()
                .filter(r -> r.status() == EvaluationStatus.DRAFT)
                .toList();
    }

    private void setManagerContext(Seed s) {
        TenantContextHolder.set(new TenantContext(
                s.actorId, s.tenantId, Set.of(s.projectId),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("EVALUATION_READ", "EVALUATION_EDIT", "EVALUATION_PANEL_MANAGE"),
                Set.of(), false, "ru-RU"));
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
        // Approval requires >= 2 levels per factor.
        factorLevelService.add(f1.id(), new FactorLevelCommand(
                "L1", 1, new BigDecimal("100"), null, Map.of("ru-RU", "L1"), null));
        factorLevelService.add(f1.id(), new FactorLevelCommand(
                "L2", 2, new BigDecimal("500"), null, Map.of("ru-RU", "L2"), null));
        approveMethodologyUseCase.approve(versionId);

        TenantContextHolder.clear();
        return new Seed(tenant, proj.getId(), dpt.getId(), pos.getId(), versionId, actor);
    }
}
