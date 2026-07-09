package uz.hrlab.grading.reporting.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import uz.hrlab.grading.access.application.ActorNameResolver;
import uz.hrlab.grading.access.infrastructure.UserRepository;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogRepository;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.organization.domain.DepartmentStatus;
import uz.hrlab.grading.organization.domain.DepartmentType;
import uz.hrlab.grading.organization.infrastructure.DepartmentHierarchyResolver;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.reporting.application.template.EvaluationReportFilter;
import uz.hrlab.grading.reporting.application.template.ReportDataPort;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Port-level unit test for the EVALUATION_SUMMARY grading enrichment in
 * {@link DefaultReportDataPort#loadEvaluations}. Proves the derivation that lives
 * ONLY in the port (not the renderer): the Departament/Bo'limi department split
 * (top-level ancestor vs own leaf), per-evaluator enrichment (resolved FIO,
 * localized role, UTC evaluation date, methodology label, panel id), and the
 * panel-average gating (an AVERAGED panel yields one PanelAverageRow; an AWAITING
 * panel with no materialized averages yields none). All repository access is
 * tenant-scoped through mocks.
 */
class DefaultReportDataPortGradingTest {

    private final PositionRepository positions = mock(PositionRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final EvaluationRepository evaluations = mock(EvaluationRepository.class);
    private final EvaluationScoreRepository evaluationScores = mock(EvaluationScoreRepository.class);
    private final FactorRepository factors = mock(FactorRepository.class);
    private final FactorLevelRepository factorLevels = mock(FactorLevelRepository.class);
    private final MethodologyVersionRepository methodologyVersions =
            mock(MethodologyVersionRepository.class);
    private final MethodologyRepository methodologies = mock(MethodologyRepository.class);
    private final GradeStructureRepository gradeStructures = mock(GradeStructureRepository.class);
    private final GradeRepository grades = mock(GradeRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final SystemAuditLogRepository auditLog = mock(SystemAuditLogRepository.class);
    private final ActorNameResolver actorNames = mock(ActorNameResolver.class);
    private final PanelRepository panels = mock(PanelRepository.class);
    private final PanelFactorAverageRepository panelFactorAverages =
            mock(PanelFactorAverageRepository.class);

    private final DefaultReportDataPort port = new DefaultReportDataPort(
            positions, projects, evaluations, evaluationScores, factors, factorLevels,
            methodologyVersions, methodologies, gradeStructures, grades, departments,
            users, tenants, auditLog, actorNames, panels, panelFactorAverages,
            new DepartmentHierarchyResolver(departments));

    private final UUID tenantId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    private final UUID mvId = UUID.randomUUID();
    private final UUID methodologyId = UUID.randomUUID();
    private final UUID knowledgeFactorId = UUID.randomUUID();

    // Department tree: ROOT "IT department" → CHILD "Backend division".
    private final UUID rootDeptId = UUID.randomUUID();
    private final UUID childDeptId = UUID.randomUUID();

    private final UUID nestedPositionId = UUID.randomUUID();   // under CHILD dept
    private final UUID topLevelPositionId = UUID.randomUUID();  // under ROOT dept

    private final UUID averagedPanelId = UUID.randomUUID();
    private final UUID awaitingPanelId = UUID.randomUUID();

    private final UUID evaluatorA = UUID.randomUUID();
    private final UUID evaluatorB = UUID.randomUUID();
    private final UUID draftEvaluator = UUID.randomUUID();

    @Test
    void enrichesDepartmentSplitDatesNamesAndPanelAverageRow() {
        stubCommon();

        // Three evaluations:
        //  - nested position, AVERAGED panel, submitted (date filled)
        //  - top-level position, AWAITING panel, submitted
        //  - standalone DRAFT (no panel, submittedAt null ⇒ blank date)
        OffsetDateTime submitted =
                OffsetDateTime.of(2026, 5, 10, 9, 0, 0, 0, ZoneOffset.UTC);
        EvaluationJpaEntity nested = ev(nestedPositionId, evaluatorA,
                EvaluatorRole.HR_DIRECTOR, averagedPanelId, EvaluationStatus.APPROVED,
                submitted, 130, 3);
        EvaluationJpaEntity topLevel = ev(topLevelPositionId, evaluatorB,
                EvaluatorRole.HR_DIRECTOR, awaitingPanelId, EvaluationStatus.SUBMITTED,
                submitted.plusDays(2), 170, null);
        EvaluationJpaEntity draft = ev(topLevelPositionId, draftEvaluator,
                EvaluatorRole.ADDITIONAL, null, EvaluationStatus.DRAFT, null, 70, null);

        when(evaluations.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page(nested, topLevel, draft));

        when(actorNames.resolveAll(eq(tenantId), any())).thenReturn(Map.of(
                evaluatorA, "Alice Director",
                evaluatorB, "Bob Reviewer",
                draftEvaluator, "Eve Drafter"));

        // Scores keyed by factor id → mapped back to the KNOWLEDGE code in the port.
        when(evaluationScores.findAllByTenantIdAndEvaluationIdIn(eq(tenantId), any()))
                .thenReturn(List.of(
                        score(nested.getId(), knowledgeFactorId, "60"),
                        score(topLevel.getId(), knowledgeFactorId, "80")));

        // BE-19 — the port now batch-loads per-factor averages + panels for the whole
        // page in ONE tenant-scoped query each. Only the AVERAGED panel has
        // materialized averages; the AWAITING panel contributes no average row, so it
        // simply does not appear in the batched result (⇒ no PanelAverageRow).
        when(panelFactorAverages.findAllByTenantIdAndPanelIdIn(eq(tenantId), any()))
                .thenReturn(List.of(avg(averagedPanelId, knowledgeFactorId, "61")));
        when(panels.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(panel(averagedPanelId, EvaluationPanelStatus.APPROVED,
                        submitted.plusDays(1), new BigDecimal("130"), 3)));

        ReportDataPort.EvaluationMatrix matrix =
                port.loadEvaluations(tenantId, projectId, "en-US", EvaluationReportFilter.none());

        // --- Departament / Bo'limi split -----------------------------------
        ReportDataPort.EvaluationRow nestedRow = rowForEvaluator(matrix, "Alice Director");
        assertThat(nestedRow.departmentName()).isEqualTo("IT department"); // top-level ancestor
        assertThat(nestedRow.divisionName()).isEqualTo("Backend division"); // own leaf (nested)

        ReportDataPort.EvaluationRow topRow = rowForEvaluator(matrix, "Bob Reviewer");
        assertThat(topRow.departmentName()).isEqualTo("IT department");
        assertThat(topRow.divisionName()).isEmpty(); // top-level position ⇒ Bo'limi blank

        // --- Per-evaluator enrichment --------------------------------------
        assertThat(nestedRow.evaluatorRole()).isEqualTo("HR director"); // localized, not enum
        assertThat(nestedRow.evaluationDate()).isEqualTo("2026-05-10"); // UTC yyyy-MM-dd
        assertThat(nestedRow.methodologyVersionLabel()).isEqualTo("Classic 8-factor (v2)");
        assertThat(nestedRow.panelId()).isEqualTo(averagedPanelId);
        assertThat(nestedRow.scoresByFactorCode()).containsEntry("KNOWLEDGE", "60");

        // DRAFT (submittedAt null) ⇒ blank evaluation date; no panel.
        ReportDataPort.EvaluationRow draftRow = rowForEvaluator(matrix, "Eve Drafter");
        assertThat(draftRow.evaluationDate()).isEmpty();
        assertThat(draftRow.panelId()).isNull();

        // --- Panel average gating ------------------------------------------
        // Exactly ONE average row (the AVERAGED panel); the AWAITING panel yields none.
        assertThat(matrix.panelAverages()).hasSize(1);
        ReportDataPort.PanelAverageRow avgRow = matrix.panelAverages().get(0);
        assertThat(avgRow.panelId()).isEqualTo(averagedPanelId);
        assertThat(avgRow.avgScoresByFactorCode()).containsEntry("KNOWLEDGE", "61");
        assertThat(avgRow.totalScore()).isEqualTo("130");
        assertThat(avgRow.status()).isEqualTo("Approved");
        assertThat(avgRow.evaluationDate()).isEqualTo("2026-05-11");
        assertThat(avgRow.gradeCode()).isEqualTo("G3"); // APPROVED panel ⇒ grade filled
        assertThat(avgRow.gradeName()).isEqualTo("G3"); // no grade structure ⇒ code fallback
    }

    // --- stubs / fixtures -------------------------------------------------

    private void stubCommon() {
        when(projects.findByIdAndTenantId(projectId, tenantId)).thenReturn(Optional.empty());

        // One methodology version with a single KNOWLEDGE factor.
        FactorJpaEntity knowledge = new FactorJpaEntity(knowledgeFactorId, tenantId, mvId,
                "KNOWLEDGE", new BigDecimal("20"), new BigDecimal("100"), 0, true);
        knowledge.setNameI18n(Map.of("en-US", "Knowledge"));
        when(factors.findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(tenantId, mvId))
                .thenReturn(List.of(knowledge));

        MethodologyVersionJpaEntity version = mock(MethodologyVersionJpaEntity.class);
        when(version.getVersionNumber()).thenReturn(2);
        when(version.getMethodologyId()).thenReturn(methodologyId);
        when(methodologyVersions.findByIdAndTenantId(mvId, tenantId))
                .thenReturn(Optional.of(version));
        MethodologyJpaEntity methodology = mock(MethodologyJpaEntity.class);
        when(methodology.getNameI18n()).thenReturn(Map.of("en-US", "Classic 8-factor"));
        when(methodology.getCode()).thenReturn("CLASSIC8");
        when(methodologies.findByIdAndTenantId(methodologyId, tenantId))
                .thenReturn(Optional.of(methodology));

        // Positions: nested under CHILD dept, top-level under ROOT dept.
        PositionJpaEntity nestedPos = new PositionJpaEntity(nestedPositionId, tenantId, projectId,
                childDeptId, "POS-001", Map.of("en-US", "Software engineer"),
                null, null, null, null, PositionStatus.ACTIVE);
        PositionJpaEntity topPos = new PositionJpaEntity(topLevelPositionId, tenantId, projectId,
                rootDeptId, "POS-002", Map.of("en-US", "Senior engineer"),
                null, null, null, null, PositionStatus.ACTIVE);
        when(positions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(nestedPos, topPos));

        // Department closure: leaf load returns CHILD + ROOT; parent level returns ROOT.
        DepartmentJpaEntity root = dept(rootDeptId, null, "IT department");
        DepartmentJpaEntity child = dept(childDeptId, rootDeptId, "Backend division");
        // The port batch-loads { childDeptId, rootDeptId } (both leaf ids) first,
        // then the unseen parents (none new, since root is already present).
        when(departments.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(child, root));

        // No active grade structure ⇒ grade names fall back to "G"+number.
        when(gradeStructures.findAllByTenantIdAndProjectIdAndStatusOrderByVersionNumberDesc(
                eq(tenantId), eq(projectId), any())).thenReturn(List.of());
    }

    private EvaluationJpaEntity ev(UUID positionId, UUID evaluatorUserId, EvaluatorRole role,
                                   UUID panelId, EvaluationStatus status,
                                   OffsetDateTime submittedAt, int total, Integer gradeNumber) {
        EvaluationJpaEntity e = new EvaluationJpaEntity(UUID.randomUUID(), tenantId, projectId,
                positionId, mvId, evaluatorUserId, status);
        e.setEvaluatorRole(role);
        e.setPanelId(panelId);
        e.setSubmittedAt(submittedAt);
        e.setDisplayedTotalScore(new BigDecimal(total));
        e.setAssignedGradeNumber(gradeNumber);
        return e;
    }

    private EvaluationScoreJpaEntity score(UUID evalId, UUID factorId, String raw) {
        return new EvaluationScoreJpaEntity(UUID.randomUUID(), tenantId, evalId, factorId,
                UUID.randomUUID(), new BigDecimal(raw));
    }

    private PanelFactorAverageJpaEntity avg(UUID panelId, UUID factorId, String raw) {
        return new PanelFactorAverageJpaEntity(UUID.randomUUID(), tenantId, panelId, factorId,
                new BigDecimal(raw), 3);
    }

    private EvaluationPanelJpaEntity panel(UUID id, EvaluationPanelStatus status,
                                           OffsetDateTime averagedAt, BigDecimal total,
                                           Integer gradeNumber) {
        EvaluationPanelJpaEntity p = new EvaluationPanelJpaEntity(id, tenantId, projectId,
                nestedPositionId, mvId, status, 3);
        p.setAveragedAt(averagedAt);
        p.setDisplayedTotalScore(total);
        p.setAssignedGradeNumber(gradeNumber);
        return p;
    }

    private DepartmentJpaEntity dept(UUID id, UUID parentId, String enName) {
        return new DepartmentJpaEntity(id, tenantId, projectId, parentId, enName,
                Map.of("en-US", enName), DepartmentType.DEPARTMENT, DepartmentStatus.ACTIVE);
    }

    private Page<EvaluationJpaEntity> page(EvaluationJpaEntity... evs) {
        List<EvaluationJpaEntity> content = List.of(evs);
        return new PageImpl<>(content, PageRequest.of(0, 1_000), content.size());
    }

    private ReportDataPort.EvaluationRow rowForEvaluator(
            ReportDataPort.EvaluationMatrix matrix, String evaluatorName) {
        return matrix.rows().stream()
                .filter(r -> evaluatorName.equals(r.evaluatorName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row for " + evaluatorName));
    }
}
