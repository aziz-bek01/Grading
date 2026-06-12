package uz.hrlab.grading.evaluation.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;
import uz.hrlab.grading.evaluation.domain.PanelAssignment;
import uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Wire shape for one roster seat (snake_case + NON_NULL). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PanelAssignmentResponse(
        UUID id,
        UUID panelId,
        UUID evaluatorUserId,
        String evaluatorName,
        EvaluatorRole evaluatorRole,
        PanelAssignmentStatus assignmentStatus,
        UUID evaluationId,
        OffsetDateTime updatedAt
) {
    public static PanelAssignmentResponse from(PanelAssignment a, String evaluatorName) {
        return new PanelAssignmentResponse(
                a.id(), a.panelId(), a.evaluatorUserId(), evaluatorName,
                a.evaluatorRole(), a.assignmentStatus(), a.evaluationId(), a.updatedAt());
    }
}
