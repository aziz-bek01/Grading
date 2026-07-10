package uz.hrlab.grading.workflow.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.project.domain.ProjectStatus;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;
import uz.hrlab.grading.workflow.domain.ProjectWorkflow;
import uz.hrlab.grading.workflow.domain.WorkflowStage;
import uz.hrlab.grading.workflow.domain.WorkflowStageStatus;
import uz.hrlab.grading.workflow.infrastructure.ProjectWorkflowJpaEntity;
import uz.hrlab.grading.workflow.infrastructure.ProjectWorkflowRepository;
import uz.hrlab.grading.workflow.infrastructure.ProjectWorkflowStageJpaEntity;
import uz.hrlab.grading.workflow.infrastructure.ProjectWorkflowStageRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the workflow recompute algorithm — exercises each of the 11
 * stage rules with mocked sub-domain counts.
 */
@Tag("workflow")
class WorkflowRecomputeServiceTest {

    private ProjectRepository projects;
    private ProjectWorkflowRepository workflows;
    private ProjectWorkflowStageRepository stages;
    private WorkflowEntityCounts counts;
    private AuditService audit;
    private WorkflowRecomputeService service;

    private UUID tenantId;
    private UUID projectId;
    private ProjectJpaEntity project;

    @BeforeEach
    void setup() {
        projects = mock(ProjectRepository.class);
        workflows = mock(ProjectWorkflowRepository.class);
        stages = mock(ProjectWorkflowStageRepository.class);
        counts = mock(WorkflowEntityCounts.class);
        audit = mock(AuditService.class);
        service = new WorkflowRecomputeService(projects, workflows, stages, counts, audit);

        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        Map<String, String> nameI18n = new HashMap<>();
        nameI18n.put("ru-RU", "Test project");
        project = new ProjectJpaEntity(projectId, tenantId, "CODE-1", nameI18n,
                "desc", ProjectStatus.ACTIVE, null, null, null);

        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(projectId), Set.of(),
                Set.of("WORKFLOW_READ", "WORKFLOW_EDIT"), Set.of(), false, "ru-RU"));

        given(projects.findByIdAndTenantId(eq(projectId), eq(tenantId)))
                .willReturn(Optional.of(project));
        given(workflows.findByTenantIdAndProjectId(eq(tenantId), eq(projectId)))
                .willReturn(Optional.empty());
        given(workflows.save(any(ProjectWorkflowJpaEntity.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(stages.save(any(ProjectWorkflowStageJpaEntity.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(stages.findAllByTenantIdAndProjectWorkflowIdOrderBySortOrderAsc(any(), any()))
                .willReturn(java.util.List.of());
    }

    private java.util.Map<WorkflowStage, WorkflowStageStatus> statusMap(ProjectWorkflow w) {
        java.util.Map<WorkflowStage, WorkflowStageStatus> m = new java.util.EnumMap<>(WorkflowStage.class);
        for (var s : w.stages()) m.put(s.stage(), s.status());
        return m;
    }

    @Test
    void emptyProject_setupNotStartedExceptMetaCheck() {
        given(counts.countDepartments(any(), any())).willReturn(0L);
        given(counts.countPositions(any(), any())).willReturn(0L);
        given(counts.countActivePositions(any(), any())).willReturn(0L);
        given(counts.countApprovedJobProfiles(any(), any())).willReturn(0L);
        given(counts.countLockedMethodologyVersionsForProject(any(), any())).willReturn(0L);
        given(counts.countEvaluationsByApprovedOrLocked(any(), any())).willReturn(0L);
        given(counts.countEvaluationsInProject(any(), any())).willReturn(0L);
        given(counts.countEvaluationsAwaitingCalibration(any(), any())).willReturn(0L);
        given(counts.countLockedGradeStructures(any(), any())).willReturn(0L);

        ProjectWorkflow w = service.recompute(projectId);
        var byStage = statusMap(w);

        // SETUP — meta is set so 50% IN_PROGRESS
        assertThat(byStage.get(WorkflowStage.SETUP)).isEqualTo(WorkflowStageStatus.IN_PROGRESS);
        assertThat(byStage.get(WorkflowStage.ORGANIZATION)).isEqualTo(WorkflowStageStatus.NOT_STARTED);
        assertThat(byStage.get(WorkflowStage.POSITIONS)).isEqualTo(WorkflowStageStatus.NOT_STARTED);
        assertThat(byStage.get(WorkflowStage.JOB_PROFILES)).isEqualTo(WorkflowStageStatus.NOT_STARTED);
        assertThat(byStage.get(WorkflowStage.METHODOLOGY)).isEqualTo(WorkflowStageStatus.NOT_STARTED);
        assertThat(byStage.get(WorkflowStage.EVALUATION)).isEqualTo(WorkflowStageStatus.NOT_STARTED);
        assertThat(byStage.get(WorkflowStage.CALIBRATION)).isEqualTo(WorkflowStageStatus.NOT_STARTED);
        assertThat(byStage.get(WorkflowStage.GRADES)).isEqualTo(WorkflowStageStatus.NOT_STARTED);
        assertThat(byStage.get(WorkflowStage.COMPENSATION)).isEqualTo(WorkflowStageStatus.LOCKED_FUTURE);
        assertThat(byStage.get(WorkflowStage.REPORTS)).isEqualTo(WorkflowStageStatus.LOCKED_FUTURE);
        assertThat(byStage.get(WorkflowStage.ARCHIVE)).isEqualTo(WorkflowStageStatus.NOT_STARTED);
    }

    @Test
    void fullyComplete_allStagesComplete() {
        // 1 department, 5 positions all active, all profiles approved, methodology
        // locked, evaluations approved, no awaiting, grade structure locked.
        given(counts.countDepartments(any(), any())).willReturn(1L);
        given(counts.countPositions(any(), any())).willReturn(5L);
        given(counts.countActivePositions(any(), any())).willReturn(5L);
        given(counts.countApprovedJobProfiles(any(), any())).willReturn(5L);
        given(counts.countLockedMethodologyVersionsForProject(any(), any())).willReturn(1L);
        given(counts.countEvaluationsByApprovedOrLocked(any(), any())).willReturn(5L);
        given(counts.countEvaluationsInProject(any(), any())).willReturn(5L);
        given(counts.countEvaluationsAwaitingCalibration(any(), any())).willReturn(0L);
        given(counts.countLockedGradeStructures(any(), any())).willReturn(1L);

        ProjectWorkflow w = service.recompute(projectId);
        var byStage = statusMap(w);

        assertThat(byStage.get(WorkflowStage.SETUP)).isEqualTo(WorkflowStageStatus.COMPLETE);
        assertThat(byStage.get(WorkflowStage.ORGANIZATION)).isEqualTo(WorkflowStageStatus.COMPLETE);
        assertThat(byStage.get(WorkflowStage.POSITIONS)).isEqualTo(WorkflowStageStatus.COMPLETE);
        assertThat(byStage.get(WorkflowStage.JOB_PROFILES)).isEqualTo(WorkflowStageStatus.COMPLETE);
        assertThat(byStage.get(WorkflowStage.METHODOLOGY)).isEqualTo(WorkflowStageStatus.COMPLETE);
        assertThat(byStage.get(WorkflowStage.EVALUATION)).isEqualTo(WorkflowStageStatus.COMPLETE);
        assertThat(byStage.get(WorkflowStage.CALIBRATION)).isEqualTo(WorkflowStageStatus.COMPLETE);
        assertThat(byStage.get(WorkflowStage.GRADES)).isEqualTo(WorkflowStageStatus.COMPLETE);
    }

    @Test
    void partialProfiles_inProgressPercent() {
        given(counts.countDepartments(any(), any())).willReturn(1L);
        given(counts.countPositions(any(), any())).willReturn(10L);
        given(counts.countActivePositions(any(), any())).willReturn(10L);
        given(counts.countApprovedJobProfiles(any(), any())).willReturn(4L);
        given(counts.countLockedMethodologyVersionsForProject(any(), any())).willReturn(0L);
        given(counts.countEvaluationsByApprovedOrLocked(any(), any())).willReturn(0L);
        given(counts.countEvaluationsInProject(any(), any())).willReturn(0L);
        given(counts.countEvaluationsAwaitingCalibration(any(), any())).willReturn(0L);
        given(counts.countLockedGradeStructures(any(), any())).willReturn(0L);

        ProjectWorkflow w = service.recompute(projectId);
        var byStage = w.stages().stream()
                .filter(s -> s.stage() == WorkflowStage.JOB_PROFILES).findFirst().orElseThrow();
        assertThat(byStage.status()).isEqualTo(WorkflowStageStatus.IN_PROGRESS);
        assertThat(byStage.completionPercent()).isEqualByComparingTo(new BigDecimal("40.00"));
    }

    @Test
    void archivedProject_archiveInProgress() {
        Map<String, String> nameI18n = new HashMap<>();
        nameI18n.put("ru-RU", "x");
        ProjectJpaEntity archived = new ProjectJpaEntity(projectId, tenantId, "C",
                nameI18n, "d", ProjectStatus.ARCHIVED, null, null, null);
        given(projects.findByIdAndTenantId(eq(projectId), eq(tenantId)))
                .willReturn(Optional.of(archived));

        given(counts.countDepartments(any(), any())).willReturn(0L);
        given(counts.countPositions(any(), any())).willReturn(0L);
        given(counts.countActivePositions(any(), any())).willReturn(0L);
        given(counts.countApprovedJobProfiles(any(), any())).willReturn(0L);
        given(counts.countLockedMethodologyVersionsForProject(any(), any())).willReturn(0L);
        given(counts.countEvaluationsByApprovedOrLocked(any(), any())).willReturn(0L);
        given(counts.countEvaluationsInProject(any(), any())).willReturn(0L);
        given(counts.countEvaluationsAwaitingCalibration(any(), any())).willReturn(0L);
        given(counts.countLockedGradeStructures(any(), any())).willReturn(0L);

        ProjectWorkflow w = service.recompute(projectId);
        var archive = w.stages().stream().filter(s -> s.stage() == WorkflowStage.ARCHIVE)
                .findFirst().orElseThrow();
        assertThat(archive.status()).isEqualTo(WorkflowStageStatus.IN_PROGRESS);
    }

    @Test
    void calibrationAwaiting_inProgress() {
        given(counts.countDepartments(any(), any())).willReturn(1L);
        given(counts.countPositions(any(), any())).willReturn(5L);
        given(counts.countActivePositions(any(), any())).willReturn(5L);
        given(counts.countApprovedJobProfiles(any(), any())).willReturn(5L);
        given(counts.countLockedMethodologyVersionsForProject(any(), any())).willReturn(1L);
        given(counts.countEvaluationsByApprovedOrLocked(any(), any())).willReturn(2L);
        given(counts.countEvaluationsInProject(any(), any())).willReturn(5L);
        given(counts.countEvaluationsAwaitingCalibration(any(), any())).willReturn(3L);
        given(counts.countLockedGradeStructures(any(), any())).willReturn(0L);

        ProjectWorkflow w = service.recompute(projectId);
        var calibration = w.stages().stream().filter(s -> s.stage() == WorkflowStage.CALIBRATION)
                .findFirst().orElseThrow();
        assertThat(calibration.status()).isEqualTo(WorkflowStageStatus.IN_PROGRESS);
    }

    // ---- BE-028: write-only-when-changed (no write-on-every-GET) ----

    /** Partial project: {@code approvedProfiles}/10 profiles approved, nothing
     *  evaluated. Yields a mix of COMPLETE, IN_PROGRESS, NOT_STARTED and
     *  LOCKED_FUTURE stages, so the scale-0 vs scale-2 completion-percent case is
     *  exercised. */
    private void stubPartialCounts(long approvedProfiles) {
        given(counts.countDepartments(any(), any())).willReturn(1L);
        given(counts.countPositions(any(), any())).willReturn(10L);
        given(counts.countActivePositions(any(), any())).willReturn(10L);
        given(counts.countApprovedJobProfiles(any(), any())).willReturn(approvedProfiles);
        given(counts.countLockedMethodologyVersionsForProject(any(), any())).willReturn(0L);
        given(counts.countEvaluationsByApprovedOrLocked(any(), any())).willReturn(0L);
        given(counts.countEvaluationsInProject(any(), any())).willReturn(0L);
        given(counts.countEvaluationsAwaitingCalibration(any(), any())).willReturn(0L);
        given(counts.countLockedGradeStructures(any(), any())).willReturn(0L);
    }

    private ProjectWorkflowJpaEntity existingWorkflow(Map<WorkflowStage, WorkflowStageMetrics> metrics) {
        return new ProjectWorkflowJpaEntity(UUID.randomUUID(), tenantId, projectId,
                WorkflowRecomputeService.computeCurrentStage(metrics), OffsetDateTime.now());
    }

    /** Build the 11 persisted rows exactly matching {@code metrics}, storing every
     *  completion percent at scale 2 to mimic a NUMERIC(5,2) reload from Postgres.
     *  This is what makes the NOT_STARTED / LOCKED_FUTURE rows hold {@code 0.00}
     *  (scale 2) while the derived metric is the scale-0 {@link BigDecimal#ZERO} —
     *  the exact historical false-dirty trigger that flushed an UPDATE per poll. */
    private List<ProjectWorkflowStageJpaEntity> existingStages(
            UUID workflowId, Map<WorkflowStage, WorkflowStageMetrics> metrics) {
        List<ProjectWorkflowStageJpaEntity> rows = new ArrayList<>(11);
        for (WorkflowStage st : WorkflowStage.values()) {
            WorkflowStageMetrics m = metrics.get(st);
            BigDecimal stored = m.completionPercent().setScale(2, RoundingMode.HALF_UP);
            rows.add(new ProjectWorkflowStageJpaEntity(UUID.randomUUID(), tenantId,
                    workflowId, projectId, st, m.status(), stored, st.sortOrder()));
        }
        return rows;
    }

    @Test
    void unchangedSnapshot_isPureRead_noWrite() {
        stubPartialCounts(4L);
        Map<WorkflowStage, WorkflowStageMetrics> metrics = service.computeAllStages(tenantId, project);
        ProjectWorkflowJpaEntity wf = existingWorkflow(metrics);
        List<ProjectWorkflowStageJpaEntity> rows = existingStages(wf.getId(), metrics);

        given(workflows.findByTenantIdAndProjectId(eq(tenantId), eq(projectId)))
                .willReturn(Optional.of(wf));
        given(stages.findAllByTenantIdAndProjectWorkflowIdOrderBySortOrderAsc(eq(tenantId), eq(wf.getId())))
                .willReturn(rows);

        // A NOT_STARTED row holds 0.00 (scale 2); the derived metric is scale-0 ZERO.
        ProjectWorkflowStageJpaEntity methodologyRow = rows.stream()
                .filter(r -> r.getStage() == WorkflowStage.METHODOLOGY).findFirst().orElseThrow();
        assertThat(methodologyRow.getStatus()).isEqualTo(WorkflowStageStatus.NOT_STARTED);
        assertThat(methodologyRow.getCompletionPercent().scale()).isEqualTo(2);

        ProjectWorkflow result = service.recompute(projectId);

        // (1) No write of any kind on an unchanged snapshot.
        verify(stages, never()).save(any());
        verify(workflows, never()).save(any());
        // The managed NOT_STARTED row was NOT re-set to a scale-0 ZERO — i.e. it was
        // never dirtied, so Hibernate would flush no UPDATE for it.
        assertThat(methodologyRow.getCompletionPercent().scale()).isEqualTo(2);
        // The read still returns the correct freshly-derived snapshot.
        var byStage = statusMap(result);
        assertThat(byStage.get(WorkflowStage.JOB_PROFILES)).isEqualTo(WorkflowStageStatus.IN_PROGRESS);
        assertThat(byStage.get(WorkflowStage.METHODOLOGY)).isEqualTo(WorkflowStageStatus.NOT_STARTED);
    }

    @Test
    void sourceChanged_recomputesWritesAndReflectsFreshValue() {
        // Persisted snapshot reflects the OLD world: 4/10 profiles approved.
        stubPartialCounts(4L);
        Map<WorkflowStage, WorkflowStageMetrics> oldMetrics = service.computeAllStages(tenantId, project);
        ProjectWorkflowJpaEntity wf = existingWorkflow(oldMetrics);
        List<ProjectWorkflowStageJpaEntity> rows = existingStages(wf.getId(), oldMetrics);

        given(workflows.findByTenantIdAndProjectId(eq(tenantId), eq(projectId)))
                .willReturn(Optional.of(wf));
        given(stages.findAllByTenantIdAndProjectWorkflowIdOrderBySortOrderAsc(eq(tenantId), eq(wf.getId())))
                .willReturn(rows);

        // A relevant source change: the remaining job profiles get approved (10/10).
        stubPartialCounts(10L);

        ProjectWorkflow result = service.recompute(projectId);

        // (2) The next read reflects the change — JOB_PROFILES is now COMPLETE.
        var jobProfiles = result.stages().stream()
                .filter(s -> s.stage() == WorkflowStage.JOB_PROFILES).findFirst().orElseThrow();
        assertThat(jobProfiles.status()).isEqualTo(WorkflowStageStatus.COMPLETE);
        assertThat(jobProfiles.completionPercent()).isEqualByComparingTo(new BigDecimal("100.00"));
        // Freshness is persisted, not just returned — a write DID happen for the change.
        verify(stages, atLeastOnce()).save(any());
    }
}
