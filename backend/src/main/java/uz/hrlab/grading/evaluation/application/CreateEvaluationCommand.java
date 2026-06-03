package uz.hrlab.grading.evaluation.application;

import java.util.UUID;

/**
 * Input for {@link CreateEvaluationUseCase}. {@code evaluatorUserId} is
 * optional — null means "evaluator = current user" (committee member
 * self-evaluating). Non-null is allowed only for HRLAB roles assigning an
 * evaluation to a committee member.
 */
public record CreateEvaluationCommand(
        UUID positionId,
        UUID methodologyVersionId,
        UUID evaluatorUserId
) {
}
