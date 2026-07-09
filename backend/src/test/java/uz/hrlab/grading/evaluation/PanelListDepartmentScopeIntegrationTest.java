package uz.hrlab.grading.evaluation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.evaluation.api.PanelResponse;
import uz.hrlab.grading.evaluation.application.PanelQueries;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
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
 * EPIC-001 — object-level authorization regression on the org-wide panel list
 * ({@code GET /api/v1/panels} → {@link PanelQueries#list}). Two departments, each
 * with its own scored panel (raw/displayed total + assigned grade populated):
 *
 * <ol>
 *   <li>a DEPARTMENT-SCOPED {@code EVALUATION_READ} holder (DEPARTMENT_MANAGER
 *       assigned ONLY department A) must NEVER receive department B's panel — nor
 *       its score / grade fields — across ANY param combo (no filter, status
 *       filter, project scope, or a direct positionId probe of the foreign
 *       department); and</li>
 *   <li>the CEO / org-wide approver ({@code EVALUATION_PANEL_APPROVE}, role
 *       CLIENT_CEO) must receive BOTH panels — the regression lock that protects
 *       the CEO Panel Overview from being narrowed.</li>
 * </ol>
 *
 * <p>This asserts the same department dimension {@code getPanelDetail} enforces
 * per-row is now enforced on the LIST, via the shared {@code DepartmentScopeFilter}.
 */
@Tag("integration")
@Tag("tenant-isolation")
@Tag("security")
class PanelListDepartmentScopeIntegrationTest extends AbstractIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired DepartmentRepository departments;
    @Autowired PositionRepository positions;
    @Autowired PanelRepository panels;
    @Autowired PanelQueries queries;

    @Autowired CreateMethodologyFromScratchUseCase createMethodologyUseCase;
    @Autowired FactorService factorService;
    @Autowired FactorLevelService factorLevelService;
    @Autowired ApproveMethodologyVersionUseCase approveMethodologyUseCase;

    private final Pageable pageable = PageRequest.of(0, 50);

    @AfterEach
    void cleanup() { TenantContextHolder.clear(); }

    @Test
    void departmentScopedReaderNeverSeesOtherDepartmentsPanelOrScore() {
        Seed s = seed();

        // Department-scoped reader assigned ONLY department A.
        setDepartmentScopedReader(s, Set.of(s.deptA));

        // (a) No filter — only department A's panel; B's id and B's grade absent.
        List<PanelResponse> noFilter = content(queries.list(null, null, null, pageable));
        assertThat(ids(noFilter)).containsExactly(s.panelA).doesNotContain(s.panelB);
        assertThat(grades(noFilter)).doesNotContain(GRADE_B);
        // The visible OWN-department row still carries its score (not blanket-nulled).
        assertThat(noFilter.get(0).rawTotalScore()).isEqualByComparingTo(RAW_A);
        assertThat(noFilter.get(0).assignedGradeNumber()).isEqualTo(GRADE_A);

        // (b) Status filter (tenant-wide status pull) — still confined to A.
        List<PanelResponse> byStatus = content(queries.list(
                null, null, List.of(EvaluationPanelStatus.AVERAGED), pageable));
        assertThat(ids(byStatus)).containsExactly(s.panelA).doesNotContain(s.panelB);

        // (c) Project scope — still confined to A (both panels share the project).
        List<PanelResponse> byProject = content(queries.list(s.projectId, null, null, pageable));
        assertThat(ids(byProject)).containsExactly(s.panelA).doesNotContain(s.panelB);

        // (d) Direct positionId probe of the FOREIGN department → zero rows (no
        // existence / score reveal via a crafted positionId).
        List<PanelResponse> probeForeign = content(queries.list(s.projectId, s.posB, null, pageable));
        assertThat(probeForeign).isEmpty();
    }

    @Test
    void ceoOrgWideApproverSeesBothDepartmentsPanels() {
        Seed s = seed();

        // Regression lock: the org-wide approver (CLIENT_CEO / EVALUATION_PANEL_APPROVE)
        // must keep FULL tenant visibility — the CEO Panel Overview stays org-wide.
        TenantContextHolder.set(new TenantContext(
                seedUser(UUID.randomUUID()), s.tenantId, Set.of(s.projectId),
                Set.of(RoleCodes.CLIENT_CEO),
                Set.of("EVALUATION_READ", "EVALUATION_PANEL_APPROVE"),
                Set.of(), false, "ru-RU"));

        assertThat(ids(content(queries.list(null, null, null, pageable))))
                .containsExactlyInAnyOrder(s.panelA, s.panelB);
        // The org-wide status pull the CEO overview uses also returns BOTH.
        assertThat(ids(content(queries.list(
                null, null, List.of(EvaluationPanelStatus.AVERAGED), pageable))))
                .containsExactlyInAnyOrder(s.panelA, s.panelB);
    }

    // --------------------------------------------------------------- helpers

    private static final BigDecimal RAW_A = new BigDecimal("742.5000");
    private static final BigDecimal RAW_B = new BigDecimal("311.0000");
    private static final int GRADE_A = 10;
    private static final int GRADE_B = 15;

    private void setDepartmentScopedReader(Seed s, Set<UUID> deptScope) {
        TenantContextHolder.set(new TenantContext(
                seedUser(UUID.randomUUID()), s.tenantId, Set.of(s.projectId),
                Set.of(RoleCodes.DEPARTMENT_MANAGER),
                Set.of("EVALUATION_READ"),
                deptScope, false, "ru-RU"));
    }

    private static List<PanelResponse> content(org.springframework.data.domain.Page<PanelResponse> p) {
        return p.getContent();
    }

    private static List<UUID> ids(List<PanelResponse> rows) {
        return rows.stream().map(PanelResponse::id).toList();
    }

    private static List<Integer> grades(List<PanelResponse> rows) {
        return rows.stream().map(PanelResponse::assignedGradeNumber).toList();
    }

    private record Seed(UUID tenantId, UUID projectId, UUID deptA, UUID deptB,
                        UUID posA, UUID posB, UUID panelA, UUID panelB) { }

    private Seed seed() {
        UUID tenant = newSeededTenantId();
        UUID actor = seedUser(UUID.randomUUID());
        ProjectJpaEntity proj = projects.save(new ProjectJpaEntity(
                UUID.randomUUID(), tenant, "EPIC1-PR", Map.of("ru-RU", "EPIC1"),
                null, ProjectStatus.ACTIVE, null, null, null));

        // Two independent root departments (A and B).
        DepartmentJpaEntity dA = departments.save(new DepartmentJpaEntity(
                UUID.randomUUID(), tenant, proj.getId(), null, "DPT-A",
                Map.of("ru-RU", "Департамент A"), DepartmentType.DEPARTMENT, DepartmentStatus.ACTIVE));
        DepartmentJpaEntity dB = departments.save(new DepartmentJpaEntity(
                UUID.randomUUID(), tenant, proj.getId(), null, "DPT-B",
                Map.of("ru-RU", "Департамент B"), DepartmentType.DEPARTMENT, DepartmentStatus.ACTIVE));

        // One position per department.
        PositionJpaEntity pA = positions.save(new PositionJpaEntity(
                UUID.randomUUID(), tenant, proj.getId(), dA.getId(), "POS-A",
                Map.of("ru-RU", "Кассир A"), null, null, null, null, PositionStatus.ACTIVE));
        PositionJpaEntity pB = positions.save(new PositionJpaEntity(
                UUID.randomUUID(), tenant, proj.getId(), dB.getId(), "POS-B",
                Map.of("ru-RU", "Кассир B"), null, null, null, null, PositionStatus.ACTIVE));

        UUID methodologyVersionId = seedApprovedMethodologyVersion(tenant, actor, proj.getId());

        // Two AVERAGED panels carrying sensitive score + grade fields.
        UUID panelA = savePanel(tenant, proj.getId(), pA.getId(), methodologyVersionId,
                RAW_A, new BigDecimal("742.50"), GRADE_A).getId();
        UUID panelB = savePanel(tenant, proj.getId(), pB.getId(), methodologyVersionId,
                RAW_B, new BigDecimal("311.00"), GRADE_B).getId();

        TenantContextHolder.clear();
        return new Seed(tenant, proj.getId(), dA.getId(), dB.getId(),
                pA.getId(), pB.getId(), panelA, panelB);
    }

    private EvaluationPanelJpaEntity savePanel(UUID tenant, UUID projectId, UUID positionId,
                                               UUID methodologyVersionId, BigDecimal raw,
                                               BigDecimal displayed, int grade) {
        EvaluationPanelJpaEntity panel = new EvaluationPanelJpaEntity(
                UUID.randomUUID(), tenant, projectId, positionId, methodologyVersionId,
                EvaluationPanelStatus.AVERAGED, 3);
        panel.setRawTotalScore(raw);
        panel.setDisplayedTotalScore(displayed);
        panel.setAssignedGradeNumber(grade);
        panel.setGradeBandId(UUID.randomUUID());
        return panels.save(panel);
    }

    /** Minimal APPROVED methodology version so the panel FK is satisfied. */
    private UUID seedApprovedMethodologyVersion(UUID tenant, UUID actor, UUID projectId) {
        TenantContextHolder.set(new TenantContext(
                actor, tenant, Set.of(projectId),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("METHODOLOGY_READ", "METHODOLOGY_CREATE", "METHODOLOGY_EDIT",
                        "METHODOLOGY_APPROVE", "METHODOLOGY_LOCK"),
                Set.of(), false, "ru-RU"));
        MethodologyAggregate agg = createMethodologyUseCase.create(new CreateMethodologyCommand(
                projectId, "EPIC1-MTH", Map.of("ru-RU", "EPIC1"), Map.of("ru-RU", "desc"),
                MethodologyType.CUSTOM, ScoringMode.DIRECT_POINTS, new BigDecimal("1000")));
        UUID versionId = agg.currentVersion().id();
        Factor f1 = factorService.add(versionId, new FactorCommand(
                "EPIC1-F1", Map.of("ru-RU", "F1"), null,
                new BigDecimal("500"), new BigDecimal("500"), 1, true));
        factorLevelService.add(f1.id(), new FactorLevelCommand(
                "L1", 1, new BigDecimal("100"), null, Map.of("ru-RU", "L1"), null));
        factorLevelService.add(f1.id(), new FactorLevelCommand(
                "L2", 2, new BigDecimal("500"), null, Map.of("ru-RU", "L2"), null));
        approveMethodologyUseCase.approve(versionId);
        TenantContextHolder.clear();
        return versionId;
    }
}
