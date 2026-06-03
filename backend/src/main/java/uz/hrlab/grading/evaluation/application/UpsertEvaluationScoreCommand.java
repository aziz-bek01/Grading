package uz.hrlab.grading.evaluation.application;

import java.util.UUID;

/** Upsert one factor's selected level (idempotent). */
public record UpsertEvaluationScoreCommand(
        UUID evaluationId,
        UUID factorId,
        UUID factorLevelId,
        String comment
) {
}
