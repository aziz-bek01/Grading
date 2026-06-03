package uz.hrlab.grading.workflow.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.project.domain.ProjectStatus;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;
import uz.hrlab.grading.workflow.domain.ProjectWorkflow;
import uz.hrlab.grading.workflow.domain.ProjectWorkflowStage;
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
import java.util.UUID;

/**
 * Derives the 11-stage workflow status from the underlying entity counts
 * (Position / JobProfile / MethodologyVersion / Evaluation / GradeStructure)
 * and persists the snapshot into {@code project_workflows} +
 * {@code project_workflow_stages}.
 *
 * <p>Read-only against business entities; ONLY mutates workflow tables.
 * Idempotent — safe to call after every business mutation. Triggered by:
 * <ul>
 *   <li>Mutation use cases via direct call (post-commit hook).</li>
 *   <li>Manual recompute endpoint ({@code POST .../workflow/recompute}).</li>
 *   <li>Initial workflow creation when first read happens.</li>
 * </ul>
 */
@Service
public class WorkflowRecomputeService {

    private final ProjectRepository projects;
    private final ProjectWorkflowRepository workflows;
    private final ProjectWorkflowStageRepository stages;
    private final WorkflowEntityCounts counts;
    private final AuditService audit;

    public WorkflowRecomputeService(ProjectRepository projects,
                                    ProjectWorkflowRepository workflows,
                                    ProjectWorkflowStageRepository stages,
                                    WorkflowEntityCounts counts,
                                    AuditService audit) {
        this.projects = projects;
        this.workflows = workflows;
        this.stages = stages;
        this.counts = counts;
        this.audit = audit;
    }

    /**
     * Recomputes the workflow for the given project under the active tenant.
     * Throws {@link TenantAccessDeniedException} if the project does not
     * belong to the active tenant.
     */
    @Transactional
    public ProjectWorkflow recompute(UUID projectId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        ProjectJpaEntity project = projects.findByIdAndTenantId(projectId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        ProjectWorkflowJpaEntity workflow = workflows
                .findByTenantIdAndProjectId(ctx.tenantId(), projectId)
                .orElseGet(() -> createWorkflowRow(ctx.tenantId(), projectId));

        // Compute metrics for each stage
        Map<WorkflowStage, WorkflowStageMetrics> metrics = computeAllStages(ctx.tenantId(), project);

        List<ProjectWorkflowStageJpaEntity> existing = stages
                .findAllByTenantIdAndProjectWorkflowIdOrderBySortOrderAsc(
                        ctx.tenantId(), workflow.getId());
        Map<WorkflowStage, ProjectWorkflowStageJpaEntity> byStage = new HashMap<>();
        for (var e : existing) byStage.put(e.getStage(), e);

        OffsetDateTime now = OffsetDateTime.now();
        List<ProjectWorkflowStageJpaEntity> persisted = new ArrayList<>(11);
        for (WorkflowStage stage : WorkflowStage.values()) {
            WorkflowStageMetrics m = metrics.get(stage);
            ProjectWorkflowStageJpaEntity row = byStage.get(stage);
            if (row == null) {
                row = new ProjectWorkflowStageJpaEntity(
                        UUID.randomUUID(), ctx.tenantId(), workflow.getId(), projectId,
                        stage, m.status(), m.completionPercent(), stage.sortOrder());
            }
            boolean changed = row.getStatus() != m.status()
                    || row.getCompletionPercent().compareTo(m.completionPercent()) != 0;
            row.setStatus(m.status());
            row.setCompletionPercent(m.completionPercent());
            if (changed) {
                row.setLastUpdatedAt(now);
                row.setLastUpdatedBy(ctx.userId());
            }
            persisted.add(stages.save(row));
        }

        // current_stage tracker: pick the first stage that isn't COMPLETE or LOCKED_FUTURE
        WorkflowStage newCurrent = computeCurrentStage(metrics);
        if (workflow.getCurrentStage() != newCurrent) {
            workflow.setCurrentStage(newCurrent);
        }
        if (project.getStatus() == ProjectStatus.ARCHIVED && workflow.getArchivedAt() == null) {
            workflow.setArchivedAt(now);
        }
        workflows.save(workflow);

        return toDomain(workflow, persisted);
    }

    /**
     * Variant intended for internal post-mutation hooks where the active
     * tenant context is already established and we want to silently swallow
     * missing-project errors (mutation rollback already happened upstream).
     */
    @Transactional
    public void recomputeQuietly(UUID projectId) {
        try {
            recompute(projectId);
        } catch (Exception ignored) {
            // Workflow snapshot is best-effort; never block the originating
            // business mutation on a recompute failure.
        }
    }

    private ProjectWorkflowJpaEntity createWorkflowRow(UUID tenantId, UUID projectId) {
        ProjectWorkflowJpaEntity workflow = new ProjectWorkflowJpaEntity(
                UUID.randomUUID(), tenantId, projectId,
                WorkflowStage.SETUP, OffsetDateTime.now());
        return workflows.save(workflow);
    }

    Map<WorkflowStage, WorkflowStageMetrics> computeAllStages(UUID tenantId, ProjectJpaEntity project) {
        UUID pid = project.getId();
        Map<WorkflowStage, WorkflowStageMetrics> out = new HashMap<>(11);

        // SETUP — project metadata + at least one department
        long depts = counts.countDepartments(tenantId, pid);
        boolean hasMeta = project.getCode() != null && project.getNameI18n() != null
                && !project.getNameI18n().isEmpty();
        if (hasMeta && depts > 0) {
            out.put(WorkflowStage.SETUP, WorkflowStageMetrics.complete());
        } else if (hasMeta) {
            out.put(WorkflowStage.SETUP, WorkflowStageMetrics.inProgress(new BigDecimal("50.00")));
        } else {
            out.put(WorkflowStage.SETUP, WorkflowStageMetrics.notStarted());
        }

        // ORGANIZATION — IN_PROGRESS once departments exist; COMPLETE when ≥1 position exists
        // (proxy for "departments approved" since Department has no APPROVED status in MVP 1)
        long positions = counts.countPositions(tenantId, pid);
        if (depts == 0) {
            out.put(WorkflowStage.ORGANIZATION, WorkflowStageMetrics.notStarted());
        } else if (positions == 0) {
            out.put(WorkflowStage.ORGANIZATION, WorkflowStageMetrics.inProgress(new BigDecimal("50.00")));
        } else {
            out.put(WorkflowStage.ORGANIZATION, WorkflowStageMetrics.complete());
        }

        // POSITIONS — based on active vs total
        long activePositions = counts.countActivePositions(tenantId, pid);
        if (positions == 0) {
            out.put(WorkflowStage.POSITIONS, WorkflowStageMetrics.notStarted());
        } else if (activePositions == positions) {
            out.put(WorkflowStage.POSITIONS, WorkflowStageMetrics.complete());
        } else {
            out.put(WorkflowStage.POSITIONS, WorkflowStageMetrics.inProgress(percent(activePositions, positions)));
        }

        // JOB_PROFILES — approved profile per position
        long approvedProfiles = counts.countApprovedJobProfiles(tenantId, pid);
        if (positions == 0) {
            out.put(WorkflowStage.JOB_PROFILES, WorkflowStageMetrics.notStarted());
        } else if (approvedProfiles >= positions) {
            out.put(WorkflowStage.JOB_PROFILES, WorkflowStageMetrics.complete());
        } else if (approvedProfiles == 0) {
            out.put(WorkflowStage.JOB_PROFILES, WorkflowStageMetrics.notStarted());
        } else {
            out.put(WorkflowStage.JOB_PROFILES, WorkflowStageMetrics.inProgress(percent(approvedProfiles, positions)));
        }

        // METHODOLOGY — at least one LOCKED version
        long lockedMv = counts.countLockedMethodologyVersionsForProject(tenantId, pid);
        if (lockedMv > 0) {
            out.put(WorkflowStage.METHODOLOGY, WorkflowStageMetrics.complete());
        } else {
            out.put(WorkflowStage.METHODOLOGY, WorkflowStageMetrics.notStarted());
        }

        // EVALUATION — (approved + locked) / total positions
        long evalsApproved = counts.countEvaluationsByApprovedOrLocked(tenantId, pid);
        if (positions == 0) {
            out.put(WorkflowStage.EVALUATION, WorkflowStageMetrics.notStarted());
        } else if (evalsApproved == 0) {
            // Could also be IN_PROGRESS if there are evaluations in DRAFT — check total
            long anyEvals = counts.countEvaluationsInProject(tenantId, pid);
            if (anyEvals == 0) {
                out.put(WorkflowStage.EVALUATION, WorkflowStageMetrics.notStarted());
            } else {
                out.put(WorkflowStage.EVALUATION, WorkflowStageMetrics.inProgress(BigDecimal.ZERO));
            }
        } else if (evalsApproved >= positions) {
            out.put(WorkflowStage.EVALUATION, WorkflowStageMetrics.complete());
        } else {
            out.put(WorkflowStage.EVALUATION, WorkflowStageMetrics.inProgress(percent(evalsApproved, positions)));
        }

        // CALIBRATION — no SUBMITTED/COMPLETE evaluations remain (everything calibrated/approved)
        long awaiting = counts.countEvaluationsAwaitingCalibration(tenantId, pid);
        long totalEvals = counts.countEvaluationsInProject(tenantId, pid);
        if (totalEvals == 0) {
            out.put(WorkflowStage.CALIBRATION, WorkflowStageMetrics.notStarted());
        } else if (awaiting == 0 && evalsApproved > 0) {
            out.put(WorkflowStage.CALIBRATION, WorkflowStageMetrics.complete());
        } else if (awaiting == 0) {
            out.put(WorkflowStage.CALIBRATION, WorkflowStageMetrics.notStarted());
        } else {
            out.put(WorkflowStage.CALIBRATION, WorkflowStageMetrics.inProgress(
                    percent(totalEvals - awaiting, totalEvals)));
        }

        // GRADES — at least one LOCKED grade structure
        long lockedGs = counts.countLockedGradeStructures(tenantId, pid);
        if (lockedGs > 0) {
            out.put(WorkflowStage.GRADES, WorkflowStageMetrics.complete());
        } else {
            out.put(WorkflowStage.GRADES, WorkflowStageMetrics.notStarted());
        }

        // COMPENSATION + REPORTS — locked for MVP 2 (Phase 7 / late MVP 2 territory)
        out.put(WorkflowStage.COMPENSATION, WorkflowStageMetrics.lockedFuture());
        out.put(WorkflowStage.REPORTS, WorkflowStageMetrics.lockedFuture());

        // ARCHIVE — IN_PROGRESS when project status = ARCHIVED, else NOT_STARTED
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            out.put(WorkflowStage.ARCHIVE, WorkflowStageMetrics.inProgress(new BigDecimal("50.00")));
        } else {
            out.put(WorkflowStage.ARCHIVE, WorkflowStageMetrics.notStarted());
        }
        return out;
    }

    static WorkflowStage computeCurrentStage(Map<WorkflowStage, WorkflowStageMetrics> metrics) {
        for (WorkflowStage stage : WorkflowStage.values()) {
            WorkflowStageStatus s = metrics.get(stage).status();
            if (s == WorkflowStageStatus.LOCKED_FUTURE) continue;
            if (s != WorkflowStageStatus.COMPLETE) return stage;
        }
        return WorkflowStage.ARCHIVE;
    }

    static BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) return BigDecimal.ZERO;
        return new BigDecimal(numerator)
                .multiply(new BigDecimal("100"))
                .divide(new BigDecimal(denominator), 2, RoundingMode.HALF_UP);
    }

    static ProjectWorkflow toDomain(ProjectWorkflowJpaEntity w,
                                    List<ProjectWorkflowStageJpaEntity> stages) {
        List<ProjectWorkflowStage> domainStages = stages.stream()
                .map(s -> new ProjectWorkflowStage(
                        s.getId(), s.getTenantId(), s.getProjectWorkflowId(), s.getProjectId(),
                        s.getStage(), s.getStatus(), s.getCompletionPercent(),
                        s.getResponsibleUserId(), s.getLastUpdatedAt(), s.getLastUpdatedBy(),
                        s.getSortOrder()))
                .toList();
        return new ProjectWorkflow(w.getId(), w.getTenantId(), w.getProjectId(),
                w.getCurrentStage(), w.getStartedAt(), w.getArchivedAt(), domainStages);
    }
}
