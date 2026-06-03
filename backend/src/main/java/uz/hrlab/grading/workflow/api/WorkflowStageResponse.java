package uz.hrlab.grading.workflow.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import uz.hrlab.grading.workflow.domain.ProjectWorkflowStage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowStageResponse(
        UUID id,
        String stage,
        String status,
        BigDecimal completionPercent,
        UUID responsibleUserId,
        OffsetDateTime lastUpdatedAt,
        UUID lastUpdatedBy,
        int sortOrder
) {
    public static WorkflowStageResponse from(ProjectWorkflowStage s) {
        return new WorkflowStageResponse(
                s.id(), s.stage().name(), s.status().name(),
                s.completionPercent(), s.responsibleUserId(),
                s.lastUpdatedAt(), s.lastUpdatedBy(), s.sortOrder());
    }
}
