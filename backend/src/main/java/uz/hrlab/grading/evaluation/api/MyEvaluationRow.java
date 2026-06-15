package uz.hrlab.grading.evaluation.api;

import uz.hrlab.grading.evaluation.domain.EvaluationStatus;

import java.util.Map;
import java.util.UUID;

/**
 * Read-only row of the evaluator self "my evaluations" inbox
 * ({@code GET /api/v1/evaluations/my}). One row per Evaluation sheet OWNED by the
 * caller (its {@code evaluator_user_id} = the authenticated user).
 *
 * <p>Carries exactly what a list UI needs to open a sheet: the evaluation id, the
 * project that owns the (project-scoped) scoring-sheet route so a cross-project
 * inbox can deep-link precisely, the panel it belongs to (null for a legacy
 * panelless sheet), the localized position
 * title (the full 4-locale map, same source the K-sheet grid resolves), the
 * evaluation status, and a filled / total factor progress count. tenant_id /
 * evaluator_user_id are NEVER echoed (sourced server-side from the security
 * context, never trusted from the client).
 */
public record MyEvaluationRow(
        UUID evaluationId,
        UUID projectId,
        UUID panelId,
        UUID positionId,
        String positionCode,
        Map<String, String> positionTitle,
        EvaluationStatus status,
        int filledFactorsCount,
        int totalFactorsCount
) {
}
