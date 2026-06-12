package uz.hrlab.grading.evaluation.application;

import java.util.UUID;

/**
 * Command to open an evaluation panel. {@code minEvaluators} is optional — null
 * defaults to the product floor of 3. Server-computed fields (totals, grade,
 * evaluator_count) are NEVER accepted here (mass-assignment guard, REQ-AVG-1).
 */
public record CreatePanelCommand(
        UUID positionId,
        UUID methodologyVersionId,
        Integer minEvaluators
) { }
