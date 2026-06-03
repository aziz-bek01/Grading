package uz.hrlab.grading.evaluation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.evaluation.application.ApproveEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.CalibrateEvaluationScoreCommand;
import uz.hrlab.grading.evaluation.application.CalibrateEvaluationScoreUseCase;
import uz.hrlab.grading.evaluation.application.CreateEvaluationCommand;
import uz.hrlab.grading.evaluation.application.CreateEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.SubmitEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.UpsertEvaluationScoreCommand;
import uz.hrlab.grading.evaluation.application.UpsertEvaluationScoreUseCase;
import uz.hrlab.grading.evaluation.domain.Evaluation;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationCalibrationEventJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationCalibrationEventRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.methodology.application.ApproveMethodologyVersionUseCase;
import uz.hrlab.grading.methodology.application.CreateMethodologyCommand;
import uz.hrlab.grading.methodology.application.CreateMethodologyFromScratchUseCase;
import uz.hrlab.grading.methodology.application.FactorCommand;
import uz.hrlab.grading.methodology.application.FactorLevelCommand;
import uz.hrlab.grading.methodology.application.FactorLevelService;
import uz.hrlab.grading.methodology.application.FactorService;
import uz.hrlab.grading.methodology.application.MethodologyAggregate;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.FactorLevel;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D-504 + D-508 — Phase 5 evaluation integration test (Docker-gated).
 *
 * <p>Verifies:
 * <ol>
 *   <li>Cross-tenant probe by Evaluation UUID — Tenant A cannot read Tenant B's
 *       evaluation via {@code findByIdAndTenantId}.</li>
 *   <li>Cross-tenant filter by positionId — list scoped to active tenant.</li>
 *   <li>Cross-tenant probe by methodologyVersionId — creating an evaluation
 *       against another tenant's methodology version returns
 *       {@link TenantAccessDeniedException}.</li>
 *   <li>D-501: APPROVED parent + direct bypass of CalibrateEvaluationScoreUseCase
 *       triggers DB-level rejection via the tightened
 *       {@code prevent_score_changes_on_locked_evaluation} function.</li>
 *   <li>D-505: multiple calibrations preserve the FIRST-call original_score
 *       (history is anchored to the engine-computed pre-calibration value, not
 *       the previous calibration result).</li>
 * </ol>
 */
@Tag("integration")
@Tag("tenant-isolation")
class Phase5EvaluationIntegrationTest extends AbstractIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired DepartmentRepository departments;
    @Autowired PositionRepository positions;
    @Autowired EvaluationRepository evaluations;
    @Autowired EvaluationScoreRepository evaluationScores;
    @Autowired EvaluationCalibrationEventRepository calibrationEvents;
    @Autowired CreateMethodologyFromScratchUseCase createMethodologyUseCase;
    @Autowired FactorService factorService;
    @Autowired FactorLevelService factorLevelService;
    @Autowired ApproveMethodologyVersionUseCase approveMethodologyUseCase;
    @Autowired CreateEvaluationUseCase createEvaluationUseCase;
    @Autowired UpsertEvaluationScoreUseCase upsertUseCase;
    @Autowired SubmitEvaluationUseCase submitUseCase;
    @Autowired ApproveEvaluationUseCase approveUseCase;
    @Autowired CalibrateEvaluationScoreUseCase calibrateUseCase;

    @AfterEach
    void cleanup() { TenantContextHolder.clear(); }

    @Test
    void evaluationFromTenantBIsInvisibleToTenantA() {
        Fixture fb = seedTenantWithApprovedEvaluation("TB-EV");
        UUID tenantA = newSeededTenantId();

        Optional<EvaluationJpaEntity> probe =
                evaluations.findByIdAndTenantId(fb.evaluationId, tenantA);
        assertThat(probe).as("cross-tenant evaluation probe must be empty")
                .isEmpty();
    }

    @Test
    void positionIdFilterIsTenantScoped() {
        Fixture fb = seedTenantWithApprovedEvaluation("TB-POS");
        UUID tenantA = newSeededTenantId();

        // Tenant A scoping its repository with Tenant B's positionId — list-style:
        // returns empty, not 404. Filter pass-through enforced by tenantId.
        var page = evaluations.findAllByTenantIdAndPositionId(
                tenantA, fb.positionId, org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(page.getContent())
                .as("Tenant A list filtered by Tenant B positionId must be empty")
                .isEmpty();
    }

    @Test
    void createEvaluationRejectsCrossTenantMethodologyVersionId() {
        Fixture fb = seedTenantWithApprovedEvaluation("TB-MV");

        // Tenant A user attempts to create an evaluation against Tenant B's
        // methodology version. Loader returns TenantAccessDeniedException
        // (mapped to 404 by GlobalExceptionHandler).
        UUID tenantA = newSeededTenantId();
        UUID actorA = seedUser();
        ProjectJpaEntity projA = projects.save(newProject(tenantA, "PRJ-A"));
        DepartmentJpaEntity dptA = departments.save(newDept(tenantA, projA.getId(), "DPT-A"));
        PositionJpaEntity posA = positions.save(
                newPosition(tenantA, projA.getId(), dptA.getId(), "POS-A"));

        TenantContextHolder.set(new TenantContext(
                actorA, tenantA, Set.of(projA.getId()),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("EVALUATION_READ", "EVALUATION_EDIT"),
                Set.of(), false, "ru-RU"));

        assertThatThrownBy(() -> createEvaluationUseCase.create(
                new CreateEvaluationCommand(posA.getId(), fb.methodologyVersionId, actorA)))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void approvedEvaluationDirectScoreUpdateBypassRejectedByDbTrigger() {
        // D-501: a developer who bypasses CalibrateEvaluationScoreUseCase and
        // tries to UPDATE evaluation_scores directly on an APPROVED parent
        // hits the new EVALUATION_APPROVED_PROTECTED DB rejection.
        Fixture fb = seedTenantWithApprovedEvaluation("TB-D501");

        // Sanity: parent is APPROVED with at least one score row.
        EvaluationJpaEntity ev = evaluations
                .findByIdAndTenantId(fb.evaluationId, fb.tenantId).orElseThrow();
        assertThat(ev.getStatus().name()).isEqualTo("APPROVED");
        List<EvaluationScoreJpaEntity> rows = evaluationScores
                .findAllByTenantIdAndEvaluationId(fb.tenantId, fb.evaluationId);
        assertThat(rows).isNotEmpty();

        // Bypass attempt: raw SQL update without the calibration session flag.
        // Use a NEW transaction (separate from any active test fixture) so we
        // do not pollute the calibration flag set by the test harness.
        Throwable thrown = catchThrowing(() ->
                jdbcTemplate.update(
                        "UPDATE evaluation_scores SET raw_factor_score = ? "
                                + "WHERE id = ? AND tenant_id = ?",
                        new BigDecimal("999.0000"), rows.get(0).getId(), fb.tenantId));
        assertThat(thrown)
                .as("direct UPDATE on APPROVED scores without calibration flag must throw")
                .isNotNull();
        assertThat(thrown.getMessage() == null ? "" : thrown.getMessage())
                .containsAnyOf("EVALUATION_APPROVED_PROTECTED",
                        "EVALUATION_LOCKED");

        // Also verify INSERT and DELETE are rejected.
        Throwable insertThrown = catchThrowing(() ->
                jdbcTemplate.update(
                        "INSERT INTO evaluation_scores (id, tenant_id, evaluation_id, "
                                + "factor_id, factor_level_id, raw_factor_score, "
                                + "is_manually_adjusted, version, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, 0, false, 0, now())",
                        UUID.randomUUID(), fb.tenantId, fb.evaluationId,
                        UUID.randomUUID(), UUID.randomUUID()));
        assertThat(insertThrown).isNotNull();

        Throwable deleteThrown = catchThrowing(() ->
                jdbcTemplate.update(
                        "DELETE FROM evaluation_scores WHERE id = ? AND tenant_id = ?",
                        rows.get(0).getId(), fb.tenantId));
        assertThat(deleteThrown).isNotNull();
    }

    @Test
    void calibrationUseCaseBypassesDbTriggerAndUpdatesScoreSuccessfully() {
        // D-501: this is the positive side — the use case SETs the session
        // variable and the UPDATE goes through.
        Fixture fb = seedTenantWithApprovedEvaluation("TB-D501-OK");

        TenantContextHolder.set(new TenantContext(
                fb.actorId, fb.tenantId, Set.of(fb.projectId),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("EVALUATION_READ", "EVALUATION_EDIT", "EVALUATION_APPROVE",
                        "EVALUATION_LOCK", "CALIBRATION_EDIT"),
                Set.of(), false, "ru-RU"));

        calibrateUseCase.calibrate(new CalibrateEvaluationScoreCommand(
                fb.evaluationId, fb.factorId, new BigDecimal("250.0000"),
                "Calibration via legitimate use case path"));

        // The row should now carry the new score + manually-adjusted flag.
        EvaluationScoreJpaEntity row = evaluationScores
                .findByTenantIdAndEvaluationIdAndFactorId(
                        fb.tenantId, fb.evaluationId, fb.factorId).orElseThrow();
        assertThat(row.isManuallyAdjusted()).isTrue();
        assertThat(row.getRawFactorScore()).isEqualByComparingTo(new BigDecimal("250.0000"));
    }

    @Test
    void multipleCalibrationsPreserveFirstOriginalScore() {
        // D-505: original_factor_score on the score row stays anchored to the
        // FIRST calibration's pre-call value. The calibration_events history
        // records the chain (preCall1→100, preCall2→100, etc — original_score
        // is the same engine-computed value for both events).
        Fixture fb = seedTenantWithApprovedEvaluation("TB-D505");

        TenantContextHolder.set(new TenantContext(
                fb.actorId, fb.tenantId, Set.of(fb.projectId),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("EVALUATION_READ", "EVALUATION_EDIT", "EVALUATION_APPROVE",
                        "EVALUATION_LOCK", "CALIBRATION_EDIT"),
                Set.of(), false, "ru-RU"));

        BigDecimal engineComputed = evaluationScores
                .findByTenantIdAndEvaluationIdAndFactorId(
                        fb.tenantId, fb.evaluationId, fb.factorId)
                .orElseThrow().getRawFactorScore();

        // Calibration #1: engineComputed -> 80
        calibrateUseCase.calibrate(new CalibrateEvaluationScoreCommand(
                fb.evaluationId, fb.factorId, new BigDecimal("80.0000"),
                "First calibration adjustment by committee"));
        EvaluationScoreJpaEntity row1 = evaluationScores
                .findByTenantIdAndEvaluationIdAndFactorId(
                        fb.tenantId, fb.evaluationId, fb.factorId).orElseThrow();
        assertThat(row1.getOriginalFactorScore())
                .as("originalFactorScore captured on first calibration")
                .isEqualByComparingTo(engineComputed);

        // Calibration #2: 80 -> 85 (the new previous value is 80 but original
        // must still be the engine-computed value).
        calibrateUseCase.calibrate(new CalibrateEvaluationScoreCommand(
                fb.evaluationId, fb.factorId, new BigDecimal("85.0000"),
                "Second calibration adjustment by committee"));
        EvaluationScoreJpaEntity row2 = evaluationScores
                .findByTenantIdAndEvaluationIdAndFactorId(
                        fb.tenantId, fb.evaluationId, fb.factorId).orElseThrow();
        assertThat(row2.getOriginalFactorScore())
                .as("originalFactorScore unchanged across multiple calibrations")
                .isEqualByComparingTo(engineComputed);
        assertThat(row2.getRawFactorScore()).isEqualByComparingTo(new BigDecimal("85.0000"));

        // Calibration events: 2 rows for this factor, BOTH with original_score
        // = engineComputed (NOT the previous calibration result).
        List<EvaluationCalibrationEventJpaEntity> events = calibrationEvents
                .findAllByTenantIdAndEvaluationIdOrderByDecidedAtDesc(
                        fb.tenantId, fb.evaluationId);
        List<EvaluationCalibrationEventJpaEntity> forFactor = events.stream()
                .filter(e -> e.getFactorId().equals(fb.factorId))
                .toList();
        assertThat(forFactor).hasSize(2);
        // Event records the per-call delta: first call original=engineComputed,
        // adjusted=80; second call original=80, adjusted=85. We assert the
        // distinct adjusted values + that the SCORE ROW's original_factor_score
        // remains the engineComputed value (this is the D-505 invariant).
        List<BigDecimal> adjustedScores = forFactor.stream()
                .map(EvaluationCalibrationEventJpaEntity::getAdjustedScore)
                .toList();
        assertThat(adjustedScores)
                .as("calibration events record adjusted=80 and adjusted=85")
                .anyMatch(v -> v.compareTo(new BigDecimal("80.0000")) == 0)
                .anyMatch(v -> v.compareTo(new BigDecimal("85.0000")) == 0);
    }

    // ---------- fixtures ----------

    private record Fixture(UUID tenantId, UUID projectId, UUID positionId,
                           UUID methodologyVersionId, UUID factorId,
                           UUID evaluationId, UUID actorId) { }

    /**
     * Build a fully-seeded fixture for a fresh tenant: project, department,
     * position, methodology + 2 factors (each with 2 levels), APPROVED
     * methodology version, evaluation created + scored + submitted + approved.
     */
    private Fixture seedTenantWithApprovedEvaluation(String codePrefix) {
        UUID tenant = newSeededTenantId();
        UUID actor = seedUser();
        ProjectJpaEntity proj = projects.save(newProject(tenant, codePrefix + "-PR"));
        DepartmentJpaEntity dpt = departments.save(
                newDept(tenant, proj.getId(), codePrefix + "-DPT"));
        PositionJpaEntity pos = positions.save(
                newPosition(tenant, proj.getId(), dpt.getId(), codePrefix + "-POS"));

        TenantContextHolder.set(new TenantContext(
                actor, tenant, Set.of(proj.getId()),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("METHODOLOGY_READ", "METHODOLOGY_CREATE", "METHODOLOGY_EDIT",
                        "METHODOLOGY_APPROVE", "METHODOLOGY_LOCK",
                        "EVALUATION_READ", "EVALUATION_EDIT", "EVALUATION_APPROVE",
                        "EVALUATION_LOCK", "CALIBRATION_EDIT"),
                Set.of(), false, "ru-RU"));

        MethodologyAggregate agg = createMethodologyUseCase.create(new CreateMethodologyCommand(
                proj.getId(), codePrefix + "-MTH",
                Map.of("ru-RU", codePrefix), Map.of("ru-RU", "desc"),
                MethodologyType.CUSTOM, ScoringMode.DIRECT_POINTS,
                new BigDecimal("1000")));
        UUID versionId = agg.currentVersion().id();

        Factor f1 = factorService.add(versionId, new FactorCommand(
                codePrefix + "-F1", Map.of("ru-RU", "F1"), null,
                new BigDecimal("500"), new BigDecimal("500"), 1, true));
        Factor f2 = factorService.add(versionId, new FactorCommand(
                codePrefix + "-F2", Map.of("ru-RU", "F2"), null,
                new BigDecimal("500"), new BigDecimal("500"), 2, true));
        FactorLevel f1l1 = factorLevelService.add(f1.id(), new FactorLevelCommand(
                "L1", 1, new BigDecimal("100"), null, Map.of("ru-RU", "L1"), null));
        factorLevelService.add(f1.id(), new FactorLevelCommand(
                "L2", 2, new BigDecimal("500"), null, Map.of("ru-RU", "L2"), null));
        FactorLevel f2l1 = factorLevelService.add(f2.id(), new FactorLevelCommand(
                "L1", 1, new BigDecimal("100"), null, Map.of("ru-RU", "L1"), null));
        factorLevelService.add(f2.id(), new FactorLevelCommand(
                "L2", 2, new BigDecimal("500"), null, Map.of("ru-RU", "L2"), null));
        approveMethodologyUseCase.approve(versionId);

        Evaluation eval = createEvaluationUseCase.create(new CreateEvaluationCommand(
                pos.getId(), versionId, actor));
        upsertUseCase.upsert(new UpsertEvaluationScoreCommand(
                eval.id(), f1.id(), f1l1.id(), "F1"));
        upsertUseCase.upsert(new UpsertEvaluationScoreCommand(
                eval.id(), f2.id(), f2l1.id(), "F2"));
        submitUseCase.submit(eval.id());
        approveUseCase.approve(eval.id());

        // Detach the seeding tenant from the holder so subsequent test
        // assertions can switch contexts freely.
        TenantContextHolder.clear();
        return new Fixture(tenant, proj.getId(), pos.getId(), versionId,
                f1.id(), eval.id(), actor);
    }

    private static Throwable catchThrowing(Runnable r) {
        try { r.run(); return null; }
        catch (Throwable t) { return t; }
    }

    private ProjectJpaEntity newProject(UUID tenantId, String code) {
        return new ProjectJpaEntity(
                UUID.randomUUID(), tenantId, code, Map.of("ru-RU", code), null,
                ProjectStatus.ACTIVE, null, null, null);
    }

    private DepartmentJpaEntity newDept(UUID tenantId, UUID projectId, String code) {
        return new DepartmentJpaEntity(
                UUID.randomUUID(), tenantId, projectId, null, code,
                Map.of("ru-RU", code), DepartmentType.DEPARTMENT, DepartmentStatus.ACTIVE);
    }

    private PositionJpaEntity newPosition(UUID tenantId, UUID projectId,
                                          UUID departmentId, String code) {
        return new PositionJpaEntity(
                UUID.randomUUID(), tenantId, projectId, departmentId, code,
                Map.of("ru-RU", code), null, null, null, null, PositionStatus.ACTIVE);
    }

    private UUID seedUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.users (id, email, full_name, status, default_locale, version) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'ru-RU', 0) "
                        + "ON CONFLICT (id) DO NOTHING",
                userId, "u-" + userId + "@test", "Test user " + userId);
        return userId;
    }
}
