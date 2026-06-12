package uz.hrlab.grading.evaluation.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Immutable snapshot of one evaluator's seat on a panel. */
public record PanelAssignment(
        UUID id,
        UUID tenantId,
        UUID panelId,
        UUID evaluatorUserId,
        EvaluatorRole evaluatorRole,
        UUID evaluationId,
        PanelAssignmentStatus assignmentStatus,
        OffsetDateTime updatedAt
) { }
