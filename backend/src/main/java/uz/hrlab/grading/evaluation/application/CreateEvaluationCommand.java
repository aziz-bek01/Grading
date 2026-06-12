package uz.hrlab.grading.evaluation.application;

import uz.hrlab.grading.evaluation.domain.EvaluatorRole;

import java.util.UUID;

/**
 * Input for {@link CreateEvaluationUseCase}. {@code evaluatorUserId} is
 * optional — null means "evaluator = current user" (committee member
 * self-evaluating). Non-null is allowed only for HRLAB roles assigning an
 * evaluation to a committee member.
 *
 * <p>MVP2 multi-evaluator — {@code panelId} + {@code evaluatorRole} are non-null
 * ONLY when the evaluation is created as one seat of a panel roster (LockRoster
 * path, BE-6). For a panelless single evaluation both are null and the legacy
 * one-active-per-position guard applies; for a panel seat the
 * one-active-per-(panel, evaluator) guard applies instead.
 */
public record CreateEvaluationCommand(
        UUID positionId,
        UUID methodologyVersionId,
        UUID evaluatorUserId,
        UUID panelId,
        EvaluatorRole evaluatorRole
) {
    /** Legacy 3-arg form — panelless single evaluation. */
    public CreateEvaluationCommand(UUID positionId, UUID methodologyVersionId,
                                   UUID evaluatorUserId) {
        this(positionId, methodologyVersionId, evaluatorUserId, null, null);
    }

    /** Panel-seat form (BE-6 LockRoster) — links the new sheet to its panel + role. */
    public static CreateEvaluationCommand forPanelSeat(UUID positionId, UUID methodologyVersionId,
                                                       UUID evaluatorUserId, UUID panelId,
                                                       EvaluatorRole role) {
        return new CreateEvaluationCommand(positionId, methodologyVersionId,
                evaluatorUserId, panelId, role);
    }
}
