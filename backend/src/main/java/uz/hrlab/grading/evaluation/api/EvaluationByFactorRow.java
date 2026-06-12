package uz.hrlab.grading.evaluation.api;

import uz.hrlab.grading.evaluation.domain.EvaluationStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-only row used by the "Excel K-sheet" UX: one row per evaluation, but
 * scoped to the single factor selected via {@code groupBy=factor&factorId=...}.
 *
 * <p>Carries enough metadata for the grid (position context + filled / total
 * counts) without exposing tenant_id (sourced server-side, never echoed).
 */
public record EvaluationByFactorRow(
        UUID evaluationId,
        UUID positionId,
        String positionCode,
        String positionTitle,
        String departmentName,
        // BE-11 (SWEEP) — wire key: unit_name. The position's broader org unit
        // (its department's PARENT in the org tree); department_name is the
        // immediate department. Null when the department is a root (no parent)
        // or the parent is not resolvable tenant-scoped. NON_NULL is NOT used on
        // this record (it is a plain row), so null serializes as JSON null — the
        // FE treats `unit_name: null` as absent (` ? ... : ''`).
        String unitName,
        UUID departmentId,
        EvaluationStatus status,
        Integer filledFactorsCount,
        Integer totalFactorsCount,
        UUID currentScoreFactorLevelId,
        BigDecimal currentRawFactorScore,
        String currentComment
) {
}
