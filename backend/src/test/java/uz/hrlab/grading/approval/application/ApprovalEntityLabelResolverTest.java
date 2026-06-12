package uz.hrlab.grading.approval.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
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
        resolver = new ApprovalEntityLabelResolver(evaluations, positions, methodologyVersions,
                methodologies, gradeStructures, jobProfiles, projects);
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
    void nullArgsYieldNull() {
        assertThat(resolver.resolve(null, ApprovalEntityType.GRADE_STRUCTURE, UUID.randomUUID()))
                .isNull();
        assertThat(resolver.resolve(tenantA, null, UUID.randomUUID())).isNull();
        assertThat(resolver.resolve(tenantA, ApprovalEntityType.GRADE_STRUCTURE, null)).isNull();
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
