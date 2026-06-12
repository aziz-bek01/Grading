package uz.hrlab.grading.evaluation.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Open a panel. {@code minEvaluators} optional (defaults to 3). NO server-computed
 * fields are accepted (mass-assignment guard, REQ-AVG-1): totals, evaluator_count,
 * grade are never client-supplied; tenantId is server-derived (ArchitectureTest).
 */
public record CreatePanelRequest(
        @NotNull UUID positionId,
        @NotNull UUID methodologyVersionId,
        @Min(1) Integer minEvaluators
) { }
