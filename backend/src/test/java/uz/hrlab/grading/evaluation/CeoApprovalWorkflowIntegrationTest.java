package uz.hrlab.grading.evaluation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.approval.application.ApprovalDecisionMaker;
import uz.hrlab.grading.approval.application.ApprovalQueries;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequest;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalStepJpaEntity;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogJpaEntity;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogRepository;
import uz.hrlab.grading.evaluation.application.PanelApprovalOutcomeListener;
import uz.hrlab.grading.evaluation.application.SubmitPanelToCeoUseCase;
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
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.gradestructure.domain.GradeBandGapPolicy;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatus;
import uz.hrlab.grading.gradestructure.domain.GradeStructureType;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA-004 (release blocker) — full-stack, real-Postgres regression lock on the CEO
 * approval DECISION pipeline: {@code ApprovalDecisionMaker} (REAL, not mocked) →
 * {@code PanelApprovalOutcomeListener} (REAL Spring bean, REAL wiring) →
 * {@code ApprovePanelUseCase} / {@code ReopenPanelUseCase} / {@code
 * ResetPanelToCollectingUseCase}.
 *
 * <p>Every prior test of this pipeline ({@code ApprovalDecisionMakerTest},
 * {@code PanelApprovalOutcomeListenerTest}, {@code ResetPanelToCollectingUseCaseTest},
 * {@code ApprovePanelUseCaseTest}) mocks its collaborators. A Spring-wiring
 * regression (the listener not registered, the wrong use case invoked, a
 * transaction boundary silently swallowing the cascade) would pass 100% of that
 * mocked suite while corrupting production data — this class is the missing
 * real-wired lock, with special emphasis on the IRREVERSIBLE REJECT path (deletes
 * every evaluator's sheet + scores and resets the panel to COLLECTING).
 *
 * <p>Docker-gated (via {@link AbstractIntegrationTest} + {@code RequiresDocker}):
 * SKIPS (not fails) with no local Docker daemon; runs against a real Testcontainers
 * Postgres in CI.
 *
 * <p>Seeding reuses the SAME patterns as {@code DeletePanelDetachesEvaluationsIntegrationTest}
 * (direct panel / evaluation / score / assignment JPA-entity seeding) and
 * {@code PanelListDepartmentScopeIntegrationTest} (direct AVERAGED-panel seeding +
 * {@code TenantContext} construction for manager / CEO actors) — no new seeding
 * scaffolding is invented. The {@code AVERAGED -> SUBMITTED} transition + the
 * opening of the single-step CEO approval request is driven through the REAL
 * {@link SubmitPanelToCeoUseCase} (which itself reuses the production
 * {@code CeoPanelApprovalOpener} / {@code CreateApprovalRequestUseCase}), so the
 * approval request + step under test are the actual production shape, not a
 * hand-rolled stand-in.
 *
 * <h3>Verified production behavior (as of this test, file:line references in the
 * javadoc of each collaborator)</h3>
 * <ul>
 *   <li><b>REJECT</b> — {@code ApprovalDecisionMaker.decide()} (REJECT case) sets
 *       the approval request REJECTED and fires {@code PanelApprovalOutcomeListener}
 *       → {@code ResetPanelToCollectingUseCase.onRejected}: DELETES every
 *       per-evaluator sheet + its scores (via {@code DeleteEvaluationUseCase
 *       .deleteForSystem}, which only deletes sheets in a PRE-SUBMISSION status —
 *       DRAFT/INCOMPLETE/COMPLETE; a sheet already individually SUBMITTED would be
 *       silently SKIPPED, see {@code EvaluationStatus.isDeletable()}), clears
 *       {@code panel_factor_averages} + the stored totals, resets (does NOT
 *       delete) non-WITHDRAWN roster seats to ASSIGNED with {@code evaluation_id =
 *       null}, and flips the panel {@code SUBMITTED -> COLLECTING}. Audited as
 *       {@code EVALUATION_PANEL_RESET_TO_DRAFT}.</li>
 *   <li><b>APPROVE</b> — {@code ApprovePanelUseCase.onApproved}: panel {@code
 *       SUBMITTED -> APPROVED}, grade assigned via {@code
 *       EvaluationGradeAssignmentService.assignToPanel} against the panel's
 *       averaged {@code raw_total_score}, and every contributing sheet
 *       ({@code contributesToPanelResult()} — COMPLETE/SUBMITTED/APPROVED/LOCKED)
 *       is flipped to LOCKED. Audited as {@code EVALUATION_PANEL_APPROVED}.</li>
 *   <li><b>REQUEST_CHANGES</b> — {@code ReopenPanelUseCase.onChangesRequested}:
 *       panel {@code SUBMITTED -> AWAITING_EVALUATIONS}, ONLY the aggregates
 *       ({@code panel_factor_averages} + the stored totals) are cleared — the
 *       per-evaluator sheets and their {@code evaluation_scores} are left
 *       completely untouched (non-destructive re-review). Audited as {@code
 *       EVALUATION_PANEL_REOPENED}.</li>
 * </ul>
 *
 * <p>Wiring verdict: CORRECT. {@code ApprovalDecisionMaker} is a plain
 * {@code @Service} with a constructor-injected {@code List<ApprovalOutcomeListener>}
 * that Spring populates with every {@code @Component} implementing the interface
 * — {@code PanelApprovalOutcomeListener} included — with no manual bean wiring
 * step to forget. This test exercises that exact Spring-managed list.
 */
@Tag("integration")
@Tag("audit")
@Tag("workflow")
class CeoApprovalWorkflowIntegrationTest extends AbstractIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired DepartmentRepository departments;
    @Autowired PositionRepository positions;
    @Autowired CreateMethodologyFromScratchUseCase createMethodologyUseCase;
    @Autowired FactorService factorService;
    @Autowired FactorLevelService factorLevelService;
    @Autowired ApproveMethodologyVersionUseCase approveMethodologyUseCase;

    @Autowired GradeStructureRepository gradeStructures;
    @Autowired GradeRepository grades;
    @Autowired GradeBandRepository gradeBands;

    @Autowired PanelRepository panels;
    @Autowired PanelAssignmentRepository assignments;
    @Autowired EvaluationRepository evaluations;
    @Autowired EvaluationScoreRepository scores;
    @Autowired PanelFactorAverageRepository panelFactorAverages;

    @Autowired SubmitPanelToCeoUseCase submitPanelToCeoUseCase;
    @Autowired ApprovalQueries approvalQueries;
    /** The REAL decision core — never mocked in this test. */
    @Autowired ApprovalDecisionMaker approvalDecisionMaker;
    /** The REAL, Spring-wired listener — never mocked in this test. */
    @Autowired PanelApprovalOutcomeListener panelApprovalOutcomeListener;

    @Autowired SystemAuditLogRepository auditLog;

    private static final BigDecimal PANEL_RAW_TOTAL = new BigDecimal("300.0000");
    private static final BigDecimal PANEL_DISPLAYED_TOTAL = new BigDecimal("300.00");
    private static final int GRADE_NUMBER = 7;

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    void ceoRejectIsDestructiveDeletesAllScoresAndResetsPanelToCollecting() {
        assertThat(panelApprovalOutcomeListener).as("real Spring-wired listener, never mocked").isNotNull();

        Seed s = seed("CEO-REJ");
        PanelUnderReview p = seedPanelAwaitingCeo(s);

        setCeoContext(s);
        ApprovalRequest decided = approvalDecisionMaker.reject(p.approvalRequestId(), p.approvalStepId(),
                "CEO rejects the panel result; evaluation restarts from scratch.");
        TenantContextHolder.clear();

        assertThat(decided.currentStatus()).isEqualTo(ApprovalRequestStatus.REJECTED);

        EvaluationPanelJpaEntity panel = panels.findByIdAndTenantId(p.panelId(), s.tenantId()).orElseThrow();
        assertThat(panel.getStatus()).isEqualTo(EvaluationPanelStatus.COLLECTING);
        assertThat(panel.getRawTotalScore()).isNull();
        assertThat(panel.getDisplayedTotalScore()).isNull();
        assertThat(panel.getAveragedAt()).isNull();
        assertThat(panel.getAveragedBy()).isNull();

        // DESTRUCTIVE: every evaluator sheet — and its scores — is physically gone.
        assertThat(evaluations.findAllByTenantIdAndPanelId(s.tenantId(), p.panelId())).isEmpty();
        for (UUID evaluationId : p.evaluationIds()) {
            assertThat(evaluations.findByIdAndTenantId(evaluationId, s.tenantId())).isEmpty();
            assertThat(scores.findAllByTenantIdAndEvaluationId(s.tenantId(), evaluationId)).isEmpty();
        }

        // panel_factor_averages cleared.
        assertThat(panelFactorAverages.findAllByTenantIdAndPanelId(s.tenantId(), p.panelId())).isEmpty();

        // Roster KEPT (not deleted) but reset — ASSIGNED, evaluation_id nulled, so
        // the manager can re-pick experts and re-lock a fresh DRAFT sheet.
        List<PanelAssignmentJpaEntity> roster =
                assignments.findAllByTenantIdAndPanelId(s.tenantId(), p.panelId());
        assertThat(roster).hasSize(3);
        assertThat(roster).allSatisfy(seat -> {
            assertThat(seat.getAssignmentStatus()).isEqualTo(PanelAssignmentStatus.ASSIGNED);
            assertThat(seat.getEvaluationId()).isNull();
        });

        // Audit — the surviving record of the rejection, hash-chained (append-only,
        // security-blueprint §9.2/§20.1). Exactly 3 EVALUATION_DELETED rows (one per
        // deleted sheet) plus the panel-level EVALUATION_PANEL_RESET_TO_DRAFT.
        assertHashChained(s.tenantId(), AuditAction.EVALUATION_PANEL_RESET_TO_DRAFT, p.panelId());
        assertThat(auditRowsByAction(s.tenantId(), AuditAction.EVALUATION_DELETED))
                .extracting(SystemAuditLogJpaEntity::getEntityId)
                .containsExactlyInAnyOrderElementsOf(p.evaluationIds());
        assertThat(auditRowsByAction(s.tenantId(), AuditAction.APPROVAL_STEP_REJECTED))
                .extracting(SystemAuditLogJpaEntity::getEntityId)
                .containsExactly(p.approvalStepId());
    }

    @Test
    void ceoApproveLocksContributingSheetsAndAssignsGrade() {
        assertThat(panelApprovalOutcomeListener).as("real Spring-wired listener, never mocked").isNotNull();

        Seed s = seed("CEO-APR");
        PanelUnderReview p = seedPanelAwaitingCeo(s);

        setCeoContext(s);
        ApprovalRequest decided = approvalDecisionMaker.approve(p.approvalRequestId(), p.approvalStepId(),
                "Approved — averaged result reviewed and signed off.");
        TenantContextHolder.clear();

        assertThat(decided.currentStatus()).isEqualTo(ApprovalRequestStatus.APPROVED);

        EvaluationPanelJpaEntity panel = panels.findByIdAndTenantId(p.panelId(), s.tenantId()).orElseThrow();
        assertThat(panel.getStatus()).isEqualTo(EvaluationPanelStatus.APPROVED);
        assertThat(panel.getApprovedAt()).isNotNull();
        assertThat(panel.getApprovedBy()).isEqualTo(s.ceoUserId());
        // Grade assigned from the averaged score via the reused band-lookup service.
        assertThat(panel.getAssignedGradeNumber()).isEqualTo(GRADE_NUMBER);
        assertThat(panel.getGradeBandId()).isNotNull();
        // The averaged total itself is untouched by approve (only reject/reopen clear it).
        assertThat(panel.getRawTotalScore()).isEqualByComparingTo(PANEL_RAW_TOTAL);

        // Immutability symmetry — every contributing (COMPLETE) sheet is LOCKED.
        List<EvaluationJpaEntity> sheets = evaluations.findAllByTenantIdAndPanelId(s.tenantId(), p.panelId());
        assertThat(sheets).hasSize(3);
        assertThat(sheets).allSatisfy(sheet -> {
            assertThat(sheet.getStatus()).isEqualTo(EvaluationStatus.LOCKED);
            assertThat(sheet.getLockedBy()).isEqualTo(s.ceoUserId());
            assertThat(sheet.getLockedAt()).isNotNull();
        });

        // Scores are NEVER touched on approve.
        for (UUID evaluationId : p.evaluationIds()) {
            assertThat(scores.findAllByTenantIdAndEvaluationId(s.tenantId(), evaluationId)).hasSize(1);
        }

        assertHashChained(s.tenantId(), AuditAction.EVALUATION_PANEL_APPROVED, p.panelId());
    }

    @Test
    void ceoRequestChangesPreservesScoresAndReopensPanelForEvaluators() {
        assertThat(panelApprovalOutcomeListener).as("real Spring-wired listener, never mocked").isNotNull();

        Seed s = seed("CEO-CHG");
        PanelUnderReview p = seedPanelAwaitingCeo(s);

        setCeoContext(s);
        ApprovalRequest decided = approvalDecisionMaker.requestChanges(p.approvalRequestId(),
                p.approvalStepId(),
                "CEO requests changes: please re-verify the department-director scoring.");
        TenantContextHolder.clear();

        assertThat(decided.currentStatus()).isEqualTo(ApprovalRequestStatus.CHANGES_REQUESTED);

        EvaluationPanelJpaEntity panel = panels.findByIdAndTenantId(p.panelId(), s.tenantId()).orElseThrow();
        assertThat(panel.getStatus()).isEqualTo(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        assertThat(panel.getRawTotalScore()).isNull();
        assertThat(panel.getDisplayedTotalScore()).isNull();
        assertThat(panel.getAveragedAt()).isNull();
        assertThat(panel.getAveragedBy()).isNull();
        // Never approved via this path.
        assertThat(panel.getAssignedGradeNumber()).isNull();

        // Aggregates cleared...
        assertThat(panelFactorAverages.findAllByTenantIdAndPanelId(s.tenantId(), p.panelId())).isEmpty();

        // ...but the per-evaluator sheets + their scores are PRESERVED — the
        // NON-destructive sibling of reject.
        List<EvaluationJpaEntity> sheets = evaluations.findAllByTenantIdAndPanelId(s.tenantId(), p.panelId());
        assertThat(sheets).extracting(EvaluationJpaEntity::getId)
                .containsExactlyInAnyOrderElementsOf(p.evaluationIds());
        assertThat(sheets).allSatisfy(
                sheet -> assertThat(sheet.getStatus()).isEqualTo(EvaluationStatus.COMPLETE));
        for (UUID evaluationId : p.evaluationIds()) {
            assertThat(scores.findAllByTenantIdAndEvaluationId(s.tenantId(), evaluationId)).hasSize(1);
        }

        // Roster is untouched by reopen (only ResetPanelToCollectingUseCase mutates it).
        List<PanelAssignmentJpaEntity> roster =
                assignments.findAllByTenantIdAndPanelId(s.tenantId(), p.panelId());
        assertThat(roster).hasSize(3);
        assertThat(roster).allSatisfy(
                seat -> assertThat(seat.getAssignmentStatus()).isEqualTo(PanelAssignmentStatus.COMPLETED));

        assertHashChained(s.tenantId(), AuditAction.EVALUATION_PANEL_REOPENED, p.panelId());
        assertThat(auditRowsByAction(s.tenantId(), AuditAction.APPROVAL_STEP_CHANGES_REQUESTED))
                .extracting(SystemAuditLogJpaEntity::getEntityId)
                .containsExactly(p.approvalStepId());
    }

    // --------------------------------------------------------------- helpers

    private record Seed(UUID tenantId, UUID projectId, UUID departmentId, UUID positionId,
                        UUID methodologyVersionId, UUID factorId, UUID factorLevelId,
                        UUID managerActorId, UUID ceoUserId) { }

    private record PanelUnderReview(UUID panelId, List<UUID> evaluationIds,
                                    UUID approvalRequestId, UUID approvalStepId) { }

    private Seed seed(String codePrefix) {
        UUID tenant = newSeededTenantId();
        UUID manager = seedUser(UUID.randomUUID());
        UUID ceo = seedUser(UUID.randomUUID());

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
                manager, tenant, Set.of(proj.getId()),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("METHODOLOGY_READ", "METHODOLOGY_CREATE", "METHODOLOGY_EDIT",
                        "METHODOLOGY_APPROVE", "METHODOLOGY_LOCK"),
                Set.of(), false, "ru-RU"));
        MethodologyAggregate agg = createMethodologyUseCase.create(new CreateMethodologyCommand(
                proj.getId(), codePrefix + "-MTH", Map.of("ru-RU", codePrefix), Map.of("ru-RU", "desc"),
                MethodologyType.CUSTOM, ScoringMode.DIRECT_POINTS, new BigDecimal("1000")));
        UUID versionId = agg.currentVersion().id();
        Factor f1 = factorService.add(versionId, new FactorCommand(
                codePrefix + "-F1", Map.of("ru-RU", "F1"), null,
                new BigDecimal("500"), new BigDecimal("500"), 1, true));
        // Approval requires >= 2 levels per factor.
        FactorLevel l1 = factorLevelService.add(f1.id(), new FactorLevelCommand(
                "L1", 1, new BigDecimal("100"), null, Map.of("ru-RU", "L1"), null));
        factorLevelService.add(f1.id(), new FactorLevelCommand(
                "L2", 2, new BigDecimal("500"), null, Map.of("ru-RU", "L2"), null));
        approveMethodologyUseCase.approve(versionId);
        TenantContextHolder.clear();

        // An APPROVED grade structure covering the panel's averaged total, so the
        // CEO-approve path assigns a REAL grade (REQ-CEO-3 / EvaluationGradeAssignmentService).
        //
        // The 018 immutability trigger (prevent_grade_changes_on_locked_structure)
        // REJECTS any grade / grade_band write once the parent structure is
        // APPROVED/LOCKED/ARCHIVED. So seed the structure DRAFT, insert the grade +
        // band while it is still mutable, THEN transition DRAFT -> APPROVED (an
        // allowed status move per prevent_grade_structure_status_regression). The
        // structure still ends APPROVED, so the grade-assignment path is unchanged.
        // saveAndFlush pins the SQL order (grade/band INSERT before the status
        // UPDATE) so Hibernate's action-queue ordering cannot flush the APPROVED
        // UPDATE ahead of the child INSERTs and re-trip the trigger.
        GradeStructureJpaEntity structure = gradeStructures.saveAndFlush(new GradeStructureJpaEntity(
                UUID.randomUUID(), tenant, proj.getId(), codePrefix + "-GS",
                GradeStructureType.CUSTOM, GradeStructureStatus.DRAFT,
                1, null, GradeBandGapPolicy.STRICT_NO_GAPS));
        GradeJpaEntity grade = grades.saveAndFlush(new GradeJpaEntity(
                UUID.randomUUID(), tenant, structure.getId(), GRADE_NUMBER, 1));
        gradeBands.saveAndFlush(new GradeBandJpaEntity(
                UUID.randomUUID(), tenant, grade.getId(), structure.getId(),
                new BigDecimal("0.0000"), new BigDecimal("1000.0000")));
        structure.setStatus(GradeStructureStatus.APPROVED);
        gradeStructures.saveAndFlush(structure);

        return new Seed(tenant, proj.getId(), dpt.getId(), pos.getId(), versionId,
                f1.id(), l1.id(), manager, ceo);
    }

    /**
     * Seeds a panel already AVERAGED (3 COMPLETE evaluator sheets, each with a real
     * score row and a COMPLETED roster seat, plus a materialized
     * {@code panel_factor_averages} row) and drives it through the REAL
     * {@link SubmitPanelToCeoUseCase#submit} — the exact production entrypoint —
     * so it lands {@code AVERAGED -> SUBMITTED} with a genuine, PENDING single-step
     * {@code EVALUATION_PANEL} CEO approval request open (no hand-rolled
     * ApprovalRequest/Step scaffolding).
     */
    private PanelUnderReview seedPanelAwaitingCeo(Seed s) {
        EvaluationPanelJpaEntity panel = new EvaluationPanelJpaEntity(
                UUID.randomUUID(), s.tenantId(), s.projectId(), s.positionId(),
                s.methodologyVersionId(), EvaluationPanelStatus.AVERAGED, 3);
        panel.setRawTotalScore(PANEL_RAW_TOTAL);
        panel.setDisplayedTotalScore(PANEL_DISPLAYED_TOTAL);
        panel.setAveragedAt(OffsetDateTime.now());
        panel.setAveragedBy(s.managerActorId());
        panels.save(panel);

        panelFactorAverages.save(new PanelFactorAverageJpaEntity(
                UUID.randomUUID(), s.tenantId(), panel.getId(), s.factorId(),
                new BigDecimal("100.0000"), 3));

        List<UUID> evaluationIds = new ArrayList<>();
        for (EvaluatorRole role : List.of(EvaluatorRole.HR_DIRECTOR,
                EvaluatorRole.DEPARTMENT_DIRECTOR, EvaluatorRole.EXTERNAL_EXPERT)) {
            UUID evaluatorUserId = seedUser(UUID.randomUUID());

            EvaluationJpaEntity sheet = new EvaluationJpaEntity(
                    UUID.randomUUID(), s.tenantId(), s.projectId(), s.positionId(),
                    s.methodologyVersionId(), evaluatorUserId, EvaluationStatus.COMPLETE);
            sheet.setPanelId(panel.getId());
            sheet.setEvaluatorRole(role);
            evaluations.save(sheet);
            evaluationIds.add(sheet.getId());

            scores.save(new EvaluationScoreJpaEntity(
                    UUID.randomUUID(), s.tenantId(), sheet.getId(),
                    s.factorId(), s.factorLevelId(), new BigDecimal("100.0000")));

            PanelAssignmentJpaEntity seat = new PanelAssignmentJpaEntity(
                    UUID.randomUUID(), s.tenantId(), panel.getId(), evaluatorUserId, role,
                    PanelAssignmentStatus.COMPLETED);
            seat.setEvaluationId(sheet.getId());
            assignments.save(seat);
        }

        // REAL production entrypoint: AVERAGED -> SUBMITTED + opens the single-step
        // CEO approval request (reuses CeoPanelApprovalOpener / CreateApprovalRequestUseCase).
        setManagerContext(s);
        submitPanelToCeoUseCase.submit(panel.getId());
        TenantContextHolder.clear();

        ApprovalRequestJpaEntity request = approvalQueries.findActivePendingForEntity(
                s.tenantId(), ApprovalEntityType.EVALUATION_PANEL, panel.getId());
        assertThat(request)
                .as("SubmitPanelToCeoUseCase must open a PENDING CEO EVALUATION_PANEL approval request")
                .isNotNull();
        ApprovalStepJpaEntity step =
                approvalQueries.findCurrentPendingStep(s.tenantId(), request.getId());
        assertThat(step).as("the single CEO approval step must be PENDING").isNotNull();

        return new PanelUnderReview(panel.getId(), evaluationIds, request.getId(), step.getId());
    }

    private void setManagerContext(Seed s) {
        TenantContextHolder.set(new TenantContext(
                s.managerActorId(), s.tenantId(), Set.of(s.projectId()),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("EVALUATION_READ", "EVALUATION_EDIT", "EVALUATION_PANEL_MANAGE"),
                Set.of(), false, "ru-RU"));
    }

    private void setCeoContext(Seed s) {
        TenantContextHolder.set(new TenantContext(
                s.ceoUserId(), s.tenantId(), Set.of(s.projectId()),
                Set.of(RoleCodes.CLIENT_CEO),
                Set.of("APPROVAL_REQUEST_DECIDE", "EVALUATION_PANEL_APPROVE", "EVALUATION_READ"),
                Set.of(), false, "ru-RU"));
    }

    /** All audit rows of {@code tenantId} matching {@code action}, newest first. */
    private List<SystemAuditLogJpaEntity> auditRowsByAction(UUID tenantId, String action) {
        return auditLog.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(r -> action.equals(r.getAction()))
                .toList();
    }

    /**
     * Asserts the audit row {@code (action, entityId)} exists, carries a non-blank
     * {@code hash_current}, and its {@code hash_prev} correctly points at some
     * OTHER row's {@code hash_current} in the SAME tenant chain (SHA-256 hash
     * chaining, security-blueprint §9.2/§20.1 — append-only, tamper-evident).
     */
    private void assertHashChained(UUID tenantId, String action, UUID entityId) {
        List<SystemAuditLogJpaEntity> rows = auditLog.findByTenantIdOrderByCreatedAtDesc(tenantId);
        SystemAuditLogJpaEntity target = rows.stream()
                .filter(r -> action.equals(r.getAction()) && entityId.equals(r.getEntityId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "expected an audit row action=" + action + " entityId=" + entityId));
        assertThat(target.getHashCurrent()).isNotBlank();
        assertThat(target.getHashPrev())
                .as("must chain off a prior hash — not the head of a fresh chain")
                .isNotBlank();
        boolean chained = rows.stream().anyMatch(r -> target.getHashPrev().equals(r.getHashCurrent()));
        assertThat(chained)
                .as("hash_prev must equal some OTHER row's hash_current in the same tenant chain")
                .isTrue();
    }
}
