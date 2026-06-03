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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

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
}
