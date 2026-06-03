package uz.hrlab.grading.workflow.application;

import java.math.BigDecimal;
import uz.hrlab.grading.workflow.domain.WorkflowStageStatus;

/**
 * Computed snapshot for a single workflow stage. {@link WorkflowRecomputeService}
 * builds one of these per stage from the sub-domain repositories and persists
 * it into {@code project_workflow_stages}.
 */
public record WorkflowStageMetrics(WorkflowStageStatus status, BigDecimal completionPercent) {

    public static WorkflowStageMetrics notStarted() {
        return new WorkflowStageMetrics(WorkflowStageStatus.NOT_STARTED, BigDecimal.ZERO);
    }

    public static WorkflowStageMetrics inProgress(BigDecimal percent) {
        return new WorkflowStageMetrics(WorkflowStageStatus.IN_PROGRESS, percent);
    }

    public static WorkflowStageMetrics complete() {
        return new WorkflowStageMetrics(WorkflowStageStatus.COMPLETE, new BigDecimal("100.00"));
    }

    public static WorkflowStageMetrics lockedFuture() {
        return new WorkflowStageMetrics(WorkflowStageStatus.LOCKED_FUTURE, BigDecimal.ZERO);
    }
}
