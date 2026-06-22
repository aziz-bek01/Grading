package uz.hrlab.grading.evaluation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogJpaEntity;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogRepository;
import uz.hrlab.grading.evaluation.application.ApprovePanelUseCase;
import uz.hrlab.grading.evaluation.application.AssignEvaluatorUseCase;
import uz.hrlab.grading.evaluation.application.CreatePanelCommand;
import uz.hrlab.grading.evaluation.application.CreatePanelUseCase;
import uz.hrlab.grading.evaluation.application.LockRosterUseCase;
import uz.hrlab.grading.evaluation.application.PanelQueries;
import uz.hrlab.grading.evaluation.application.ReopenApprovedPanelForExpertUseCase;
import uz.hrlab.grading.evaluation.application.UpsertEvaluationScoreCommand;
import uz.hrlab.grading.evaluation.application.UpsertEvaluationScoreUseCase;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
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

/**
 * Feature 2 (Docker-gated) — the KEY DB-trigger / GUC verification for reopening
 * an APPROVED panel to add an additional expert.
 *
 * <p>Builds a real panel through the use cases up to APPROVED (3 mandatory
 * evaluators score, panel auto-averages, {@link ApprovePanelUseCase} approves +
 * LOCKs the contributing sheets), then exercises
 * {@link ReopenApprovedPanelForExpertUseCase} and asserts:
 * <ol>
 *   <li>all existing {@code evaluation_scores} are PRESERVED (none deleted);</li>
 *   <li>prior contributing sheets are un-locked LOCKED-&gt;COMPLETE, and a
 *       subsequent {@code UpsertEvaluationScore} on a previously-LOCKED sheet
 *       SUCCEEDS — this is the DB-trigger / 043 GUC carve-out working end to end
 *       (the carve-out is enabled ONLY inside the reopen tx; the later edit
 *       succeeds because the sheet is now COMPLETE, not because the GUC leaks);</li>
 *   <li>the added ADDITIONAL expert gets a DRAFT evaluation and the panel stays
 *       AWAITING_EVALUATIONS (NOT auto-AVERAGED — the new seat is incomplete);</li>
 *   <li>once the expert completes, the re-average includes the new expert
 *       (denominator 3 -&gt; 4) and the panel flips back to AVERAGED;</li>
 *   <li>an {@code EVALUATION_PANEL_REOPENED_FOR_EXPERT} audit row is written.</li>
 * </ol>
 */
@Tag("integration")
@Tag("panel-reopen")
class PanelReopenForExpertIntegrationTest extends AbstractIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired DepartmentRepository departments;
    @Autowired PositionRepository positions;
    @Autowired CreateMethodologyFromScratchUseCase createMethodologyUseCase;
    @Autowired FactorService factorService;
    @Autowired FactorLevelService factorLevelService;
    @Autowired ApproveMethodologyVersionUseCase approveMethodologyUseCase;

    @Autowired CreatePanelUseCase createPanelUseCase;
    @Autowired AssignEvaluatorUseCase assignEvaluatorUseCase;
    @Autowired LockRosterUseCase lockRosterUseCase;
    @Autowired UpsertEvaluationScoreUseCase upsertUseCase;
    @Autowired ApprovePanelUseCase approvePanelUseCase;
    @Autowired ReopenApprovedPanelForExpertUseCase reopenForExpertUseCase;
    @Autowired PanelQueries panelQueries;

    @Autowired PanelRepository panels;
    @Autowired PanelAssignmentRepository assignments;
    @Autowired EvaluationRepository evaluations;
    @Autowired EvaluationScoreRepository scores;
    @Autowired SystemAuditLogRepository auditLog;

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    void reopenFromApprovedPreservesScoresUnlocksSheetsAndAddsExpert() {
        Fixture fx = seedApprovedMethodology("RFX");
        setManagerContext(fx);
        UUID panelId = buildApprovedPanel(fx);

        // Pre-reopen: 3 LOCKED contributing sheets, each with 2 score rows.
        List<EvaluationJpaEntity> beforeSheets =
                evaluations.findAllByTenantIdAndPanelId(fx.tenantId, panelId);
        assertThat(beforeSheets).hasSize(3)
                .allMatch(e -> e.getStatus() == EvaluationStatus.LOCKED);
        long scoreRowsBefore = countAllScoreRows(fx.tenantId, beforeSheets);
        assertThat(scoreRowsBefore).isEqualTo(6); // 3 sheets * 2 factors

        EvaluationPanelJpaEntity approved = panels.findByIdAndTenantId(panelId, fx.tenantId)
                .orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(EvaluationPanelStatus.APPROVED);
        Integer gradeBefore = approved.getAssignedGradeNumber();

        // ---- REOPEN FOR EXPERT ----
        UUID expert = seedUser(UUID.randomUUID());
        EvaluationPanel reopened = reopenForExpertUseCase.reopen(
                panelId, expert, "Adding an external SME after the first round");

        // (1) scores PRESERVED — none deleted by reopen.
        List<EvaluationJpaEntity> afterSheets =
                evaluations.findAllByTenantIdAndPanelId(fx.tenantId, panelId);
        long scoreRowsAfter = countAllScoreRows(fx.tenantId, afterSheets);
        assertThat(scoreRowsAfter)
                .as("reopen must NOT delete any evaluation_scores")
                .isEqualTo(scoreRowsBefore);

        // (2) prior contributing sheets un-locked LOCKED -> COMPLETE.
        List<EvaluationJpaEntity> priorSheets = afterSheets.stream()
                .filter(e -> !e.getEvaluatorUserId().equals(expert))
                .toList();
        assertThat(priorSheets).hasSize(3)
                .allMatch(e -> e.getStatus() == EvaluationStatus.COMPLETE);

        // (3) panel reopened: AWAITING_EVALUATIONS, totals cleared, grade HELD,
        //     new ADDITIONAL DRAFT seat present (panel NOT auto-AVERAGED).
        assertThat(reopened.status()).isEqualTo(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        EvaluationPanelJpaEntity afterPanel = panels.findByIdAndTenantId(panelId, fx.tenantId)
                .orElseThrow();
        assertThat(afterPanel.getStatus()).isEqualTo(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        assertThat(afterPanel.getRawTotalScore()).isNull();
        assertThat(afterPanel.getAssignedGradeNumber()).isEqualTo(gradeBefore); // grade held

        PanelAssignmentJpaEntity expertSeat = assignments
                .findByTenantIdAndPanelIdAndEvaluatorUserId(fx.tenantId, panelId, expert)
                .orElseThrow();
        assertThat(expertSeat.getEvaluatorRole()).isEqualTo(EvaluatorRole.ADDITIONAL);
        assertThat(expertSeat.getAssignmentStatus()).isEqualTo(PanelAssignmentStatus.ASSIGNED);
        EvaluationJpaEntity expertSheet = evaluations
                .findByIdAndTenantId(expertSeat.getEvaluationId(), fx.tenantId)
                .orElseThrow();
        assertThat(expertSheet.getStatus()).isEqualTo(EvaluationStatus.DRAFT);

        // (4) a subsequent UpsertEvaluationScore on a previously-LOCKED sheet now
        //     SUCCEEDS (the score-lock trigger keyed off status=LOCKED is lifted
        //     now that the sheet is COMPLETE — proving the un-lock end to end, and
        //     that the GUC carve-out did NOT leak past the reopen tx).
        EvaluationJpaEntity priorSheet = priorSheets.get(0);
        EvaluationScoreJpaEntity firstScore = scores
                .findAllByTenantIdAndEvaluationId(fx.tenantId, priorSheet.getId())
                .get(0);
        upsertUseCase.upsert(new UpsertEvaluationScoreCommand(
                priorSheet.getId(), firstScore.getFactorId(),
                fx.otherLevelFor(firstScore.getFactorId(), firstScore.getFactorLevelId()),
                "in-place re-score after reopen"));
        // Still 6 rows (an upsert replaces in place — never adds/removes a row).
        assertThat(countAllScoreRows(fx.tenantId,
                evaluations.findAllByTenantIdAndPanelId(fx.tenantId, panelId).stream()
                        .filter(e -> !e.getEvaluatorUserId().equals(expert)).toList()))
                .isEqualTo(6);

        // (5) audit row for the reopen.
        long reopenAudits = auditLog.search(fx.tenantId, null,
                        "EVALUATION_PANEL_REOPENED_FOR_EXPERT", "EvaluationPanel", panelId,
                        null, null, PageRequest.of(0, 10))
                .getTotalElements();
        assertThat(reopenAudits).isEqualTo(1);
    }

    @Test
    void expertCompletionReAveragesIncludingTheNewExpert() {
        Fixture fx = seedApprovedMethodology("RFX2");
        setManagerContext(fx);
        UUID panelId = buildApprovedPanel(fx);

        UUID expert = seedUser(UUID.randomUUID());
        reopenForExpertUseCase.reopen(panelId, expert, "Adding the fourth evaluator");

        // The expert scores both factors → their sheet COMPLETE → all 4 active
        // assignments complete → panel re-averages over ALL 4 contributing sheets.
        PanelAssignmentJpaEntity expertSeat = assignments
                .findByTenantIdAndPanelIdAndEvaluatorUserId(fx.tenantId, panelId, expert)
                .orElseThrow();
        UUID expertSheetId = expertSeat.getEvaluationId();
        for (UUID factorId : fx.factorIds) {
            upsertUseCase.upsert(new UpsertEvaluationScoreCommand(
                    expertSheetId, factorId, fx.firstLevelFor(factorId), "expert score"));
        }

        EvaluationPanelJpaEntity panel = panels.findByIdAndTenantId(panelId, fx.tenantId)
                .orElseThrow();
        // Consolidated one-submit flow (48f0350): completing the expert re-averages
        // (denominator 3 -> 4) AND auto-submits to the CEO in the same transaction,
        // so the panel lands on SUBMITTED. The re-average is still proven by the
        // raw total + averagedAt being (re)populated and the contributing count = 4.
        assertThat(panel.getStatus())
                .as("once the expert completes, the panel re-averages and auto-submits to the CEO")
                .isEqualTo(EvaluationPanelStatus.SUBMITTED);
        assertThat(panel.getRawTotalScore()).isNotNull();
        assertThat(panel.getAveragedAt()).isNotNull();
        long completed = assignments.findAllByTenantIdAndPanelId(fx.tenantId, panelId).stream()
                .filter(a -> a.getAssignmentStatus() == PanelAssignmentStatus.COMPLETED)
                .count();
        assertThat(completed)
                .as("the new expert is included in the re-average (denominator 3 -> 4)")
                .isEqualTo(4);
    }

    /**
     * P0-B display-integrity (end-to-end against PostgreSQL) — a non-collecting
     * (APPROVED) panel that averaged over the mandatory trio reports the
     * CONTRIBUTING denominator on BOTH the list and the detail surfaces. This also
     * exercises the new {@code PanelFactorAverageRepository.findEvaluatorCountsByPanelIds}
     * JPQL against the real DB (it is mocked in the unit test). There is no min-3
     * bypass: the panel could only reach APPROVED by averaging the 3 mandatory
     * evaluators, and that 3 is exactly what the CEO header now shows.
     */
    @Test
    void approvedPanelReportsContributingDenominatorOnListAndDetail() {
        Fixture fx = seedApprovedMethodology("RFX3");
        setManagerContext(fx);
        UUID panelId = buildApprovedPanel(fx);

        EvaluationPanelJpaEntity approved = panels.findByIdAndTenantId(panelId, fx.tenantId)
                .orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(EvaluationPanelStatus.APPROVED);

        // LIST surface — the "experts" count = the 3 contributing sheets (the
        // materialized average denominator), not just the currently-active seats.
        var listRow = panelQueries.list(fx.projectId, fx.positionId, PageRequest.of(0, 20))
                .getContent().stream()
                .filter(r -> r.id().equals(panelId))
                .findFirst()
                .orElseThrow();
        assertThat(listRow.status()).isEqualTo(EvaluationPanelStatus.APPROVED);
        assertThat(listRow.evaluatorCount())
                .as("approved panel shows the contributing denominator (3), not '1 из 1'")
                .isEqualTo(3);
        assertThat(listRow.completedCount()).isEqualTo(3);

        // DETAIL surface — the summary header N/M reflects the same denominator.
        var detail = panelQueries.getPanelDetail(panelId);
        assertThat(detail.totalCount()).isEqualTo(3);
        assertThat(detail.completedCount()).isEqualTo(3);
        assertThat(detail.panel().evaluatorCount()).isEqualTo(3);
    }

    // --------------------------------------------------------------- helpers

    /** Builds a panel, assigns the mandatory trio, locks the roster, scores each
     *  sheet to COMPLETE (auto-averaging), then approves the panel (LOCKs sheets). */
    private UUID buildApprovedPanel(Fixture fx) {
        EvaluationPanel panel = createPanelUseCase.create(
                new CreatePanelCommand(fx.positionId, fx.methodologyVersionId, 3));
        UUID panelId = panel.id();

        UUID hr = seedUser(UUID.randomUUID());
        UUID dept = seedUser(UUID.randomUUID());
        UUID ext = seedUser(UUID.randomUUID());
        assignEvaluatorUseCase.assign(panelId, hr, EvaluatorRole.HR_DIRECTOR);
        assignEvaluatorUseCase.assign(panelId, dept, EvaluatorRole.DEPARTMENT_DIRECTOR);
        assignEvaluatorUseCase.assign(panelId, ext, EvaluatorRole.EXTERNAL_EXPERT);
        lockRosterUseCase.lockRoster(panelId);

        for (PanelAssignmentJpaEntity seat : assignments
                .findAllByTenantIdAndPanelId(fx.tenantId, panelId)) {
            for (UUID factorId : fx.factorIds) {
                upsertUseCase.upsert(new UpsertEvaluationScoreCommand(
                        seat.getEvaluationId(), factorId, fx.firstLevelFor(factorId), "score"));
            }
        }
        // Consolidated one-submit panel→CEO flow (48f0350): scoring the LAST
        // required evaluator auto-averages (AWAITING_EVALUATIONS -> AVERAGED) AND
        // auto-submits to the CEO (AVERAGED -> SUBMITTED, opening the single
        // EVALUATION_PANEL approval) inside the same transaction via
        // PanelCompletionWatcher. So the panel is already SUBMITTED here — there is
        // no separate manual "submit to CEO" step, and asserting an intermediate
        // AVERAGED state is stale. ApprovePanelUseCase.onApproved then performs the
        // SUBMITTED -> APPROVED transition (it fail-softs on any other status).
        EvaluationPanelJpaEntity submitted = panels.findByIdAndTenantId(panelId, fx.tenantId)
                .orElseThrow();
        assertThat(submitted.getStatus()).isEqualTo(EvaluationPanelStatus.SUBMITTED);

        // Approve the panel (system-internal transition; locks contributing sheets).
        approvePanelUseCase.onApproved(fx.tenantId, panelId, fx.actorId);
        return panelId;
    }

    private long countAllScoreRows(UUID tenantId, List<EvaluationJpaEntity> sheets) {
        long total = 0;
        for (EvaluationJpaEntity e : sheets) {
            total += scores.findAllByTenantIdAndEvaluationId(tenantId, e.getId()).size();
        }
        return total;
    }

    private void setManagerContext(Fixture fx) {
        TenantContextHolder.set(new TenantContext(
                fx.actorId, fx.tenantId, Set.of(fx.projectId),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("EVALUATION_READ", "EVALUATION_EDIT", "EVALUATION_PANEL_MANAGE"),
                Set.of(), false, "ru-RU"));
    }

    // ---------- fixture seeding (mirrors Phase5EvaluationIntegrationTest) ----------

    private record Fixture(UUID tenantId, UUID projectId, UUID positionId,
                           UUID methodologyVersionId, UUID actorId,
                           List<UUID> factorIds,
                           Map<UUID, UUID> firstLevelByFactor,
                           Map<UUID, UUID> secondLevelByFactor) {
        UUID firstLevelFor(UUID factorId) { return firstLevelByFactor.get(factorId); }
        UUID otherLevelFor(UUID factorId, UUID current) {
            UUID first = firstLevelByFactor.get(factorId);
            return current.equals(first) ? secondLevelByFactor.get(factorId) : first;
        }
    }

    private Fixture seedApprovedMethodology(String codePrefix) {
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
        Factor f2 = factorService.add(versionId, new FactorCommand(
                codePrefix + "-F2", Map.of("ru-RU", "F2"), null,
                new BigDecimal("500"), new BigDecimal("500"), 2, true));
        FactorLevel f1l1 = factorLevelService.add(f1.id(), new FactorLevelCommand(
                "L1", 1, new BigDecimal("100"), null, Map.of("ru-RU", "L1"), null));
        FactorLevel f1l2 = factorLevelService.add(f1.id(), new FactorLevelCommand(
                "L2", 2, new BigDecimal("500"), null, Map.of("ru-RU", "L2"), null));
        FactorLevel f2l1 = factorLevelService.add(f2.id(), new FactorLevelCommand(
                "L1", 1, new BigDecimal("100"), null, Map.of("ru-RU", "L1"), null));
        FactorLevel f2l2 = factorLevelService.add(f2.id(), new FactorLevelCommand(
                "L2", 2, new BigDecimal("500"), null, Map.of("ru-RU", "L2"), null));
        approveMethodologyUseCase.approve(versionId);

        TenantContextHolder.clear();
        return new Fixture(tenant, proj.getId(), pos.getId(), versionId, actor,
                List.of(f1.id(), f2.id()),
                Map.of(f1.id(), f1l1.id(), f2.id(), f2l1.id()),
                Map.of(f1.id(), f1l2.id(), f2.id(), f2l2.id()));
    }
}
