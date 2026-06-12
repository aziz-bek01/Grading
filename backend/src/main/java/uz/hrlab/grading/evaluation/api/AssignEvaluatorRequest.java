package uz.hrlab.grading.evaluation.api;

import jakarta.validation.constraints.NotNull;
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;

import java.util.UUID;

/** Add an evaluator (user + role) to a panel roster. */
public record AssignEvaluatorRequest(
        @NotNull UUID evaluatorUserId,
        @NotNull EvaluatorRole evaluatorRole
) { }
