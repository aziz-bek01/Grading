package uz.hrlab.grading.workflow.api;

import jakarta.validation.constraints.NotNull;
import uz.hrlab.grading.workflow.domain.WorkflowStage;

public record AdvanceStageRequest(@NotNull WorkflowStage targetStage) { }
