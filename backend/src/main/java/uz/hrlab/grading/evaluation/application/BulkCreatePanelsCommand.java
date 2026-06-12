package uz.hrlab.grading.evaluation.application;

import uz.hrlab.grading.evaluation.domain.EvaluatorRole;

import java.util.List;
import java.util.UUID;

/**
 * Application command for bulk-create-panels (BE-1). One shared {@code roster}
 * applied to every position in {@code positionIds}, all against
 * {@code methodologyVersionId}. Decoupled from the API DTO so the use case has
 * no web dependency (ArchitectureTest).
 */
public record BulkCreatePanelsCommand(
        UUID methodologyVersionId,
        List<UUID> positionIds,
        List<RosterSeat> roster
) {
    /** One shared roster seat. */
    public record RosterSeat(UUID evaluatorUserId, EvaluatorRole evaluatorRole) {
    }
}
