package uz.hrlab.grading.evaluation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.application.DeletePanelUseCase;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;
import uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentRepository;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Defect — CONSTRAINT_VIOLATION on {@code DELETE /api/v1/panels/{id}} for a
 * COLLECTING panel that has per-evaluator evaluation sheets. The
 * {@code evaluations.panel_id} FK is {@code ON DELETE RESTRICT} (changelog 035),
 * so the panel row delete was blocked while any evaluation referenced it.
 *
 * <p>Docker-gated END-TO-END test against the REAL Postgres + REAL FK so the
 * RESTRICT path is actually exercised (a mocked-repo unit test cannot prove the
 * constraint is cleared). Asserts the product decision: the per-evaluator
 * evaluation is PRESERVED, not deleted — it is DETACHED ({@code panel_id} and
 * {@code evaluator_role} nulled), its {@code evaluation_scores} survive untouched,
 * and the panel + its roster seats are removed. Also re-confirms the
 * COLLECTING-only guard against the real schema.
 */
@Tag("integration")
@Tag("tenant-isolation")
class DeletePanelDetachesEvaluationsIntegrationTest extends AbstractIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired DepartmentRepository departments;
    @Autowired PositionRepository positions;
    @Autowired CreateMethodologyFromScratchUseCase createMethodologyUseCase;
    @Autowired FactorService factorService;
    @Autowired FactorLevelService factorLevelService;
    @Autowired ApproveMethodologyVersionUseCase approveMethodologyUseCase;

    @Autowired PanelRepository panels;
    @Autowired PanelAssignmentRepository assignments;
    @Autowired EvaluationRepository evaluations;
    @Autowired EvaluationScoreRepository scores;
    @Autowired DeletePanelUseCase deletePanelUseCase;

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    void deletingCollectingPanelWithEvaluationsSucceedsAndDetachesPreservingScores() {
        Seed s = seed("DPD-A");
        // The prod repro: a COLLECTING panel with 1 roster seat + 1 INCOMPLETE
        // evaluation (with a real score row) referencing the panel via the RESTRICT FK.
        UUID panelId = newPanel(s, EvaluationPanelStatus.COLLECTING);
        UUID evaluatorUserId = seedUser(UUID.randomUUID());
        newSeat(s, panelId, evaluatorUserId, EvaluatorRole.HR_DIRECTOR);
        UUID evaluationId = newEvaluation(
                s, panelId, evaluatorUserId, EvaluatorRole.HR_DIRECTOR, EvaluationStatus.INCOMPLETE);
        UUID scoreId = newScore(s, evaluationId);

        setManagerContext(s);
        // Before the fix this threw a DataIntegrityViolation (-> CONSTRAINT_VIOLATION).
        deletePanelUseCase.delete(panelId, "collecting panel clean-up before re-creation");

        // Panel and its roster seats are gone.
        assertThat(panels.findByIdAndTenantId(panelId, s.tenantId)).isEmpty();
        assertThat(assignments.findAllByTenantIdAndPanelId(s.tenantId, panelId)).isEmpty();

        // The evaluation SURVIVES, DETACHED: panel_id + evaluator_role nulled,
        // same owner, same status. Its work is never lost.
        EvaluationJpaEntity survivor = evaluations.findByIdAndTenantId(evaluationId, s.tenantId)
                .orElseThrow();
        assertThat(survivor.getPanelId()).isNull();
        assertThat(survivor.getEvaluatorRole()).isNull();
        assertThat(survivor.getEvaluatorUserId()).isEqualTo(evaluatorUserId);
        assertThat(survivor.getStatus()).isEqualTo(EvaluationStatus.INCOMPLETE);

        // The score row is untouched — the evaluator never re-scores.
        List<EvaluationScoreJpaEntity> survivingScores =
                scores.findAllByTenantIdAndEvaluationId(s.tenantId, evaluationId);
        assertThat(survivingScores).extracting(EvaluationScoreJpaEntity::getId)
                .containsExactly(scoreId);
    }

    @Test
    void deletingNonCollectingPanelIsRejectedAndNothingIsTouched() {
        Seed s = seed("DPD-B");
        UUID panelId = newPanel(s, EvaluationPanelStatus.AWAITING_EVALUATIONS);
        UUID evaluatorUserId = seedUser(UUID.randomUUID());
        newSeat(s, panelId, evaluatorUserId, EvaluatorRole.HR_DIRECTOR);
        UUID evaluationId = newEvaluation(
                s, panelId, evaluatorUserId, EvaluatorRole.HR_DIRECTOR, EvaluationStatus.COMPLETE);

        setManagerContext(s);
        assertThatThrownBy(() ->
                deletePanelUseCase.delete(panelId, "trying to delete a non-collecting panel"))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode())
                        .isEqualTo("PANEL_NOT_DELETABLE"));

        // Guard rejected before any mutation: panel, seat and the still-attached
        // evaluation are all intact.
        assertThat(panels.findByIdAndTenantId(panelId, s.tenantId)).isPresent();
        assertThat(assignments.findAllByTenantIdAndPanelId(s.tenantId, panelId)).hasSize(1);
        EvaluationJpaEntity stillAttached =
                evaluations.findByIdAndTenantId(evaluationId, s.tenantId).orElseThrow();
        assertThat(stillAttached.getPanelId()).isEqualTo(panelId);
        assertThat(stillAttached.getEvaluatorRole()).isEqualTo(EvaluatorRole.HR_DIRECTOR);
    }

    // --------------------------------------------------------------- helpers

    private UUID newPanel(Seed s, EvaluationPanelStatus status) {
        EvaluationPanelJpaEntity panel = new EvaluationPanelJpaEntity(
                UUID.randomUUID(), s.tenantId, s.projectId, s.positionId,
                s.methodologyVersionId, status, 3);
        return panels.save(panel).getId();
    }

    private void newSeat(Seed s, UUID panelId, UUID evaluatorUserId, EvaluatorRole role) {
        assignments.save(new PanelAssignmentJpaEntity(
                UUID.randomUUID(), s.tenantId, panelId, evaluatorUserId, role,
                PanelAssignmentStatus.IN_PROGRESS));
    }

    private UUID newEvaluation(Seed s, UUID panelId, UUID evaluatorUserId,
                               EvaluatorRole role, EvaluationStatus status) {
        EvaluationJpaEntity ev = new EvaluationJpaEntity(
                UUID.randomUUID(), s.tenantId, s.projectId, s.positionId,
                s.methodologyVersionId, evaluatorUserId, status);
        ev.setPanelId(panelId);
        ev.setEvaluatorRole(role);
        return evaluations.save(ev).getId();
    }

    private UUID newScore(Seed s, UUID evaluationId) {
        EvaluationScoreJpaEntity score = new EvaluationScoreJpaEntity(
                UUID.randomUUID(), s.tenantId, evaluationId,
                s.factorId, s.factorLevelId, new BigDecimal("12.0000"));
        return scores.save(score).getId();
    }

    private void setManagerContext(Seed s) {
        TenantContextHolder.set(new TenantContext(
                s.actorId, s.tenantId, Set.of(s.projectId),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("EVALUATION_READ", "EVALUATION_EDIT", "EVALUATION_PANEL_MANAGE"),
                Set.of(), false, "ru-RU"));
    }

    private record Seed(UUID tenantId, UUID projectId, UUID departmentId, UUID positionId,
                        UUID methodologyVersionId, UUID factorId, UUID factorLevelId, UUID actorId) { }

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
        // Approval requires >= 2 levels per factor; the first is the one we score against.
        FactorLevel l1 = factorLevelService.add(f1.id(), new FactorLevelCommand(
                "L1", 1, new BigDecimal("100"), null, Map.of("ru-RU", "L1"), null));
        factorLevelService.add(f1.id(), new FactorLevelCommand(
                "L2", 2, new BigDecimal("500"), null, Map.of("ru-RU", "L2"), null));
        approveMethodologyUseCase.approve(versionId);

        TenantContextHolder.clear();
        return new Seed(tenant, proj.getId(), dpt.getId(), pos.getId(),
                versionId, f1.id(), l1.id(), actor);
    }
}
