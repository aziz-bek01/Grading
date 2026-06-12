package uz.hrlab.grading.approval.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * BE-8 — tenant isolation + fail-soft tests for {@link ApprovalEntityLabelResolver}.
 *
 * <p>Every lookup is tenant-scoped via {@code findByIdAndTenantId}. A
 * foreign-tenant id (or any miss) must resolve to {@code null} — never another
 * tenant's label.
 */
@Tag("security")
class ApprovalEntityLabelResolverTest {

    private EvaluationRepository evaluations;
    private PositionRepository positions;
    private MethodologyVersionRepository methodologyVersions;
    private MethodologyRepository methodologies;
    private GradeStructureRepository gradeStructures;
    private JobProfileRepository jobProfiles;
    private ProjectRepository projects;
    private uz.hrlab.grading.evaluation.infrastructure.PanelRepository panels;
    private AbacGate abacGate;
    private ApprovalEntityLabelResolver resolver;

    private final UUID tenantA = UUID.randomUUID();

    @BeforeEach
    void setup() {
        evaluations = mock(EvaluationRepository.class);
        positions = mock(PositionRepository.class);
        methodologyVersions = mock(MethodologyVersionRepository.class);
        methodologies = mock(MethodologyRepository.class);
        gradeStructures = mock(GradeStructureRepository.class);
        jobProfiles = mock(JobProfileRepository.class);
        projects = mock(ProjectRepository.class);
        panels = mock(uz.hrlab.grading.evaluation.infrastructure.PanelRepository.class);
        // Default ABAC: allow (the mock's void enforce* methods do nothing).
        abacGate = mock(AbacGate.class);
        resolver = new ApprovalEntityLabelResolver(evaluations, panels, positions, methodologyVersions,
                methodologies, gradeStructures, jobProfiles, projects, abacGate);
        // AC-20: the title gate needs an active caller context (fail-closed
        // without one) — tests run as an in-tenant caller by default.
        // requireActive() also checks tenantId() != null, so stub it.
        TenantContext ctx = mock(TenantContext.class);
        given(ctx.tenantId()).willReturn(tenantA);
        TenantContextHolder.set(ctx);
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    void foreignTenantGradeStructureYieldsNullLabel() {
        UUID foreignId = UUID.randomUUID();
        // The row exists in tenant B; a tenant-A lookup returns empty (no leak).
        given(gradeStructures.findByIdAndTenantId(eq(foreignId), eq(tenantA)))
                .willReturn(Optional.empty());

        Map<String, String> label = resolver.resolve(tenantA,
                ApprovalEntityType.GRADE_STRUCTURE, foreignId);

        assertThat(label).isNull();
    }

    @Test
    void tenantScopedGradeStructureYieldsItsI18nName() {
        UUID id = UUID.randomUUID();
        GradeStructureJpaEntity g = mock(GradeStructureJpaEntity.class);
        given(g.getNameI18n()).willReturn(Map.of("ru-RU", "Сетка А", "en-US", "Grid A"));
        given(gradeStructures.findByIdAndTenantId(eq(id), eq(tenantA)))
                .willReturn(Optional.of(g));

        Map<String, String> label = resolver.resolve(tenantA,
                ApprovalEntityType.GRADE_STRUCTURE, id);

        assertThat(label).containsEntry("ru-RU", "Сетка А").containsEntry("en-US", "Grid A");
    }

    @Test
    void evaluationLabelComposesPositionTitleAndMethodologyName() {
        UUID evalId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID methodologyId = UUID.randomUUID();

        EvaluationJpaEntity e = mock(EvaluationJpaEntity.class);
        given(e.getPositionId()).willReturn(positionId);
        given(e.getMethodologyVersionId()).willReturn(versionId);
        given(evaluations.findByIdAndTenantId(eq(evalId), eq(tenantA))).willReturn(Optional.of(e));

        var p = mock(uz.hrlab.grading.position.infrastructure.PositionJpaEntity.class);
        given(p.getTitleI18n()).willReturn(Map.of("en-US", "Chief Accountant"));
        given(positions.findByIdAndTenantId(eq(positionId), eq(tenantA))).willReturn(Optional.of(p));

        var v = mock(uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity.class);
        given(v.getMethodologyId()).willReturn(methodologyId);
        given(v.getVersionNumber()).willReturn(3);
        given(methodologyVersions.findByIdAndTenantId(eq(versionId), eq(tenantA)))
                .willReturn(Optional.of(v));

        var m = mock(uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity.class);
        given(m.getNameI18n()).willReturn(Map.of("en-US", "HR-Lab 12"));
        given(methodologies.findByIdAndTenantId(eq(methodologyId), eq(tenantA)))
                .willReturn(Optional.of(m));

        Map<String, String> label = resolver.resolve(tenantA, ApprovalEntityType.EVALUATION, evalId);

        assertThat(label).containsEntry("en-US", "Chief Accountant · HR-Lab 12 v3");
    }

    @Test
    void panelLabelComposesPositionMethodologyAndAverageMarkerIn4Locales() {
        // BE-14 — EVALUATION_PANEL label = "<position> · <methodology> v<n> · <average>"
        // in each of the 4 supported locales, no scores/grade value (R7).
        UUID panelId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID methodologyId = UUID.randomUUID();

        var panel = mock(uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity.class);
        given(panel.getPositionId()).willReturn(positionId);
        given(panel.getMethodologyVersionId()).willReturn(versionId);
        given(panels.findByIdAndTenantId(eq(panelId), eq(tenantA))).willReturn(Optional.of(panel));

        var p = mock(uz.hrlab.grading.position.infrastructure.PositionJpaEntity.class);
        given(p.getTitleI18n()).willReturn(Map.of(
                "ru-RU", "Главный бухгалтер", "uz-Cyrl-UZ", "Бош бухгалтер",
                "uz-Latn-UZ", "Bosh buxgalter", "en-US", "Chief Accountant"));
        given(positions.findByIdAndTenantId(eq(positionId), eq(tenantA))).willReturn(Optional.of(p));

        var v = mock(uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity.class);
        given(v.getMethodologyId()).willReturn(methodologyId);
        given(v.getVersionNumber()).willReturn(3);
        given(methodologyVersions.findByIdAndTenantId(eq(versionId), eq(tenantA)))
                .willReturn(Optional.of(v));

        var m = mock(uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity.class);
        given(m.getNameI18n()).willReturn(Map.of(
                "ru-RU", "HR-Lab", "uz-Cyrl-UZ", "HR-Lab", "uz-Latn-UZ", "HR-Lab", "en-US", "HR-Lab"));
        given(methodologies.findByIdAndTenantId(eq(methodologyId), eq(tenantA)))
                .willReturn(Optional.of(m));

        Map<String, String> label = resolver.resolve(
                tenantA, ApprovalEntityType.EVALUATION_PANEL, panelId);

        assertThat(label).containsEntry("en-US", "Chief Accountant · HR-Lab v3 · average");
        assertThat(label).containsEntry("ru-RU", "Главный бухгалтер · HR-Lab v3 · среднее");
        assertThat(label).containsEntry("uz-Cyrl-UZ", "Бош бухгалтер · HR-Lab v3 · ўртача");
        assertThat(label).containsEntry("uz-Latn-UZ", "Bosh buxgalter · HR-Lab v3 · o'rtacha");
        // R7 — no scores/grade values leaked into any locale label.
        assertThat(label.values()).noneMatch(s -> s.matches(".*\\d{2,}\\.\\d.*"));
    }

    @Test
    void panelLabelDegradesToAverageMarkerWhenPositionUnresolvable() {
        // R5/R10 — a missing/forbidden position degrades the label to a generic
        // average marker rather than 404-ing the inbox.
        UUID panelId = UUID.randomUUID();
        var panel = mock(uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity.class);
        given(panel.getPositionId()).willReturn(UUID.randomUUID());
        given(panel.getMethodologyVersionId()).willReturn(UUID.randomUUID());
        given(panels.findByIdAndTenantId(eq(panelId), eq(tenantA))).willReturn(Optional.of(panel));
        given(positions.findByIdAndTenantId(any(), any())).willReturn(Optional.empty());
        given(methodologyVersions.findByIdAndTenantId(any(), any())).willReturn(Optional.empty());

        Map<String, String> label = resolver.resolve(
                tenantA, ApprovalEntityType.EVALUATION_PANEL, panelId);

        assertThat(label).containsEntry("en-US", "average");
    }

    @Test
    void foreignTenantPanelYieldsNullLabel() {
        UUID foreignPanelId = UUID.randomUUID();
        given(panels.findByIdAndTenantId(eq(foreignPanelId), eq(tenantA)))
                .willReturn(Optional.empty());
        assertThat(resolver.resolve(tenantA, ApprovalEntityType.EVALUATION_PANEL, foreignPanelId))
                .isNull();
    }

    @Test
    void nullArgsYieldNull() {
        assertThat(resolver.resolve(null, ApprovalEntityType.GRADE_STRUCTURE, UUID.randomUUID()))
                .isNull();
        assertThat(resolver.resolve(tenantA, null, UUID.randomUUID())).isNull();
        assertThat(resolver.resolve(tenantA, ApprovalEntityType.GRADE_STRUCTURE, null)).isNull();
    }

    @Test
    void deptScopedDenialDropsPositionTitleButKeepsTheRest() {
        // AC-20 / R5 — a caller outside the position's department subtree must
        // NEVER see the position title; the label degrades but stays useful.
        UUID panelId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID methodologyId = UUID.randomUUID();

        var panel = mock(uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity.class);
        given(panel.getPositionId()).willReturn(positionId);
        given(panel.getMethodologyVersionId()).willReturn(versionId);
        given(panels.findByIdAndTenantId(eq(panelId), eq(tenantA))).willReturn(Optional.of(panel));

        var p = mock(uz.hrlab.grading.position.infrastructure.PositionJpaEntity.class);
        given(positions.findByIdAndTenantId(eq(positionId), eq(tenantA))).willReturn(Optional.of(p));

        var v = mock(uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity.class);
        given(v.getMethodologyId()).willReturn(methodologyId);
        given(v.getVersionNumber()).willReturn(3);
        given(methodologyVersions.findByIdAndTenantId(eq(versionId), eq(tenantA)))
                .willReturn(Optional.of(v));

        var m = mock(uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity.class);
        given(m.getNameI18n()).willReturn(Map.of("en-US", "HR-Lab"));
        given(methodologies.findByIdAndTenantId(eq(methodologyId), eq(tenantA)))
                .willReturn(Optional.of(m));

        org.mockito.Mockito.doThrow(new PermissionDeniedException())
                .when(abacGate).enforceCanReadPosition(any(), any(), any(), any(), any());

        Map<String, String> label = resolver.resolve(
                tenantA, ApprovalEntityType.EVALUATION_PANEL, panelId);

        // Title dropped; methodology + marker remain. getTitleI18n must never
        // even be consulted on the denied path.
        assertThat(label).containsEntry("en-US", "HR-Lab v3 · average");
        org.mockito.Mockito.verify(p, org.mockito.Mockito.never()).getTitleI18n();
    }

    @Test
    void noActiveContextFailsClosedOnTheTitle() {
        // AC-20 fail-closed: without an active caller context the title is
        // dropped (resolution still never throws).
        TenantContextHolder.clear();
        UUID evalId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();

        EvaluationJpaEntity e = mock(EvaluationJpaEntity.class);
        given(e.getPositionId()).willReturn(positionId);
        given(e.getMethodologyVersionId()).willReturn(UUID.randomUUID());
        given(evaluations.findByIdAndTenantId(eq(evalId), eq(tenantA))).willReturn(Optional.of(e));

        var p = mock(uz.hrlab.grading.position.infrastructure.PositionJpaEntity.class);
        given(positions.findByIdAndTenantId(eq(positionId), eq(tenantA))).willReturn(Optional.of(p));
        given(methodologyVersions.findByIdAndTenantId(any(), any())).willReturn(Optional.empty());

        Map<String, String> label = resolver.resolve(tenantA, ApprovalEntityType.EVALUATION, evalId);

        // No readable part remains -> null label (FE degrades to type + id).
        assertThat(label).isNull();
        org.mockito.Mockito.verify(p, org.mockito.Mockito.never()).getTitleI18n();
    }

    @Test
    void failSoftWhenRepositoryThrows() {
        UUID id = UUID.randomUUID();
        given(gradeStructures.findByIdAndTenantId(any(), any()))
                .willThrow(new RuntimeException("db down"));

        // R10 — one bad referent must degrade to null, never bubble a 500.
        assertThat(resolver.resolve(tenantA, ApprovalEntityType.GRADE_STRUCTURE, id)).isNull();
    }
}
