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
 *
 * <p>Methodology fields (added for the "my evaluations" grouping UX): the
 * version the sheet is pinned to ({@code methodologyVersionId}), the container
 * that version belongs to ({@code methodologyId}), the container's localized
 * name (the full locale map, same shape as {@code positionTitle}), and the
 * container's "active" version number (highest version-number among its
 * APPROVED / LOCKED versions — matching {@code MethodologyResponse}'s
 * {@code active_version_number}). These let the inbox group rows by methodology
 * and badge the active version without a second round-trip per row.
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
        int totalFactorsCount,
        UUID methodologyVersionId,
        UUID methodologyId,
        Map<String, String> methodologyName,
        Integer methodologyActiveVersionNumber
) {
}
