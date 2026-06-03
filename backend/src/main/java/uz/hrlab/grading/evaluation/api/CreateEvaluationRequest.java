package uz.hrlab.grading.evaluation.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateEvaluationRequest(
        @NotNull UUID positionId,
        @NotNull UUID methodologyVersionId,
        UUID evaluatorUserId
) {
}
