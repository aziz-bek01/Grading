package uz.hrlab.grading.reporting;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Docker-gated integration coverage for the new EVALUATION_SUMMARY report finder
 * {@code EvaluationRepository.findForEvaluationReport} — the one place where the
 * methodology / date / evaluator filters become JPQL WHERE predicates. Skipped
 * (not failed) when no Docker daemon is reachable (see {@link RequiresDocker}).
 *
 * <p>Asserts PRD acceptance: each filter narrows; AND semantics; empty filter =
 * unfiltered regression; inclusive date bounds + null-submitted-at exclusion;
 * cross-tenant / cross-project ids → zero rows (not error).
 */
@Tag("integration")
@Tag("tenant-isolation")
class EvaluationReportFilterFinderIntegrationTest extends AbstractIntegrationTest {

    @Autowired EvaluationRepository evaluations;
    @Autowired ProjectRepository projects;
    @Autowired DepartmentRepository departments;
    @Autowired PositionRepository positions;
    @Autowired MethodologyRepository methodologies;
    @Autowired MethodologyVersionRepository methodologyVersions;

    private static final PageRequest PAGE = PageRequest.of(0, 1000);

    private static OffsetDateTime day(int y, int m, int d) {
        return OffsetDateTime.of(y, m, d, 12, 0, 0, 0, ZoneOffset.UTC);
    }

    private static OffsetDateTime startOf(int y, int m, int d) {
        return OffsetDateTime.of(y, m, d, 0, 0, 0, 0, ZoneOffset.UTC);
    }

    private static OffsetDateTime endOf(int y, int m, int d) {
        return OffsetDateTime.of(y, m, d, 23, 59, 59, 999_999_999, ZoneOffset.UTC);
    }

    @Test
    void emptyFilterReturnsAllProjectEvaluations_unfilteredRegression() {
        Fixture f = new Fixture();
        UUID e1 = f.evaluation(f.versionA, f.evaluator1, day(2026, 4, 10));
        UUID e2 = f.evaluation(f.versionB, f.evaluator2, day(2026, 5, 10));

        var page = evaluations.findForEvaluationReport(
                f.tenantId, f.projectId, null, null, null, null, PAGE);

        assertThat(page.getContent()).extracting(EvaluationJpaEntity::getId)
                .containsExactlyInAnyOrder(e1, e2);
    }

    @Test
    void methodologyVersionFilterNarrows() {
        Fixture f = new Fixture();
        UUID eA = f.evaluation(f.versionA, f.evaluator1, day(2026, 4, 10));
        f.evaluation(f.versionB, f.evaluator2, day(2026, 5, 10));

        var page = evaluations.findForEvaluationReport(
                f.tenantId, f.projectId, List.of(f.versionA), null, null, null, PAGE);

        assertThat(page.getContent()).extracting(EvaluationJpaEntity::getId)
                .containsExactly(eA);
    }

    @Test
    void evaluatorFilterNarrows() {
        Fixture f = new Fixture();
        f.evaluation(f.versionA, f.evaluator1, day(2026, 4, 10));
        UUID e2 = f.evaluation(f.versionB, f.evaluator2, day(2026, 5, 10));

        var page = evaluations.findForEvaluationReport(
                f.tenantId, f.projectId, null, List.of(f.evaluator2), null, null, PAGE);

        assertThat(page.getContent()).extracting(EvaluationJpaEntity::getId)
                .containsExactly(e2);
    }

    @Test
    void dateRangeIsInclusiveOnBothBounds() {
        Fixture f = new Fixture();
        UUID onLowerBound = f.evaluation(f.versionA, f.evaluator1, startOf(2026, 4, 1));
        UUID inside = f.evaluation(f.versionB, f.evaluator1, day(2026, 5, 15));
        UUID onUpperBound = f.evaluation(f.versionA, f.evaluator2, endOf(2026, 6, 30));
        UUID before = f.evaluation(f.versionB, f.evaluator2, day(2026, 3, 31));
        UUID after = f.evaluation(f.versionA, f.evaluator1, day(2026, 7, 1));

        var page = evaluations.findForEvaluationReport(
                f.tenantId, f.projectId, null, null,
                startOf(2026, 4, 1), endOf(2026, 6, 30), PAGE);

        assertThat(page.getContent()).extracting(EvaluationJpaEntity::getId)
                .containsExactlyInAnyOrder(onLowerBound, inside, onUpperBound)
                .doesNotContain(before, after);
    }

    @Test
    void nullSubmittedAtExcludedWhenDateFilterActive_butIncludedWhenNot() {
        Fixture f = new Fixture();
        UUID submitted = f.evaluation(f.versionA, f.evaluator1, day(2026, 5, 10));
        UUID neverSubmitted = f.evaluation(f.versionB, f.evaluator1, null);

        // With a date bound the null-submitted row is excluded.
        var bounded = evaluations.findForEvaluationReport(
                f.tenantId, f.projectId, null, null,
                startOf(2026, 1, 1), endOf(2026, 12, 31), PAGE);
        assertThat(bounded.getContent()).extracting(EvaluationJpaEntity::getId)
                .containsExactly(submitted)
                .doesNotContain(neverSubmitted);

        // With no date filter both rows are present.
        var unbounded = evaluations.findForEvaluationReport(
                f.tenantId, f.projectId, null, null, null, null, PAGE);
        assertThat(unbounded.getContent()).extracting(EvaluationJpaEntity::getId)
                .containsExactlyInAnyOrder(submitted, neverSubmitted);
    }

    @Test
    void combinedFiltersAreAndCombined() {
        Fixture f = new Fixture();
        // Only this row satisfies version=A AND evaluator=1 AND date in Q2.
        UUID match = f.evaluation(f.versionA, f.evaluator1, day(2026, 5, 1));
        UUID wrongVersion = f.evaluation(f.versionB, f.evaluator1, day(2026, 5, 1));
        UUID wrongEvaluator = f.evaluation(f.versionA, f.evaluator2, day(2026, 5, 1));
        UUID wrongDate = f.evaluation(f.versionA, f.evaluator1, day(2026, 1, 1));

        var page = evaluations.findForEvaluationReport(
                f.tenantId, f.projectId,
                List.of(f.versionA), List.of(f.evaluator1),
                startOf(2026, 4, 1), endOf(2026, 6, 30), PAGE);

        assertThat(page.getContent()).extracting(EvaluationJpaEntity::getId)
                .containsExactly(match)
                .doesNotContain(wrongVersion, wrongEvaluator, wrongDate);
    }

    @Test
    void crossTenantMethodologyVersionIdYieldsZeroRows_notError() {
        Fixture f = new Fixture();
        f.evaluation(f.versionA, f.evaluator1, day(2026, 5, 1));
        // A foreign tenant's version id passed into THIS tenant's query → 0 rows.
        Fixture other = new Fixture();

        var page = evaluations.findForEvaluationReport(
                f.tenantId, f.projectId, List.of(other.versionA), null, null, null, PAGE);

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void crossProjectEvaluatorScopedToBaseProject() {
        Fixture f = new Fixture();
        UUID inProject = f.evaluation(f.versionA, f.evaluator1, day(2026, 5, 1));
        // Same evaluator, a DIFFERENT project in the same tenant — must not bleed in.
        UUID otherProject = f.newProject();
        UUID otherDept = f.department(otherProject);
        UUID otherPos = f.position(otherProject, otherDept);
        f.evaluationIn(otherProject, otherPos, f.versionA, f.evaluator1, day(2026, 5, 1));

        var page = evaluations.findForEvaluationReport(
                f.tenantId, f.projectId, null, List.of(f.evaluator1), null, null, PAGE);

        assertThat(page.getContent()).extracting(EvaluationJpaEntity::getId)
                .containsExactly(inProject);
    }

    // ----------------------------------------------------------------- fixture

    /** Seeds tenant + users + project + methodology (2 versions) + dept + position. */
    private final class Fixture {
        final UUID tenantId = newSeededTenantId();
        final UUID evaluator1 = seedUser(UUID.randomUUID());
        final UUID evaluator2 = seedUser(UUID.randomUUID());
        final UUID projectId;
        final UUID methodologyId;
        final UUID versionA;
        final UUID versionB;
        final UUID departmentId;
        final UUID positionId;

        Fixture() {
            this.projectId = newProject();
            this.methodologyId = methodology(projectId);
            this.versionA = version(methodologyId, 1);
            this.versionB = version(methodologyId, 2);
            this.departmentId = department(projectId);
            this.positionId = position(projectId, departmentId);
        }

        UUID newProject() {
            ProjectJpaEntity p = projects.save(new ProjectJpaEntity(
                    UUID.randomUUID(), tenantId, "PR-" + UUID.randomUUID(),
                    Map.of("ru-RU", "Project"), null, ProjectStatus.ACTIVE, null, null, null));
            return p.getId();
        }

        UUID methodology(UUID project) {
            MethodologyJpaEntity m = methodologies.save(new MethodologyJpaEntity(
                    UUID.randomUUID(), tenantId, project, "MTH-" + UUID.randomUUID(),
                    MethodologyType.CUSTOM, MethodologyStatus.ACTIVE));
            return m.getId();
        }

        UUID version(UUID methodology, int number) {
            MethodologyVersionJpaEntity v = methodologyVersions.save(new MethodologyVersionJpaEntity(
                    UUID.randomUUID(), tenantId, methodology, number,
                    MethodologyVersionStatus.APPROVED, ScoringMode.DIRECT_POINTS, null, null));
            return v.getId();
        }

        UUID department(UUID project) {
            DepartmentJpaEntity d = departments.save(new DepartmentJpaEntity(
                    UUID.randomUUID(), tenantId, project, null, "DPT-" + UUID.randomUUID(),
                    Map.of("ru-RU", "Dept"), DepartmentType.DEPARTMENT, DepartmentStatus.ACTIVE));
            return d.getId();
        }

        UUID position(UUID project, UUID dept) {
            PositionJpaEntity p = positions.save(new PositionJpaEntity(
                    UUID.randomUUID(), tenantId, project, dept, "POS-" + UUID.randomUUID(),
                    Map.of("ru-RU", "Pos"), null, null, null, null, PositionStatus.ACTIVE));
            return p.getId();
        }

        UUID evaluation(UUID versionId, UUID evaluatorUserId, OffsetDateTime submittedAt) {
            return evaluationIn(projectId, positionId, versionId, evaluatorUserId, submittedAt);
        }

        UUID evaluationIn(UUID project, UUID position, UUID versionId,
                          UUID evaluatorUserId, OffsetDateTime submittedAt) {
            EvaluationJpaEntity e = new EvaluationJpaEntity(
                    UUID.randomUUID(), tenantId, project, position, versionId,
                    evaluatorUserId, EvaluationStatus.APPROVED);
            e.setSubmittedAt(submittedAt);
            return evaluations.save(e).getId();
        }
    }
}
