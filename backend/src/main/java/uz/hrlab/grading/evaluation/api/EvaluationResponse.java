package uz.hrlab.grading.evaluation.api;

import uz.hrlab.grading.evaluation.domain.Evaluation;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Single-sheet wire shape (snake_case via the global strategy). {@code panel_id}
 * and {@code evaluator_role} (P0-C) are the caller's OWN sheet metadata — they let
 * the FE render the blind banner + role chip on the single-sheet detail page. They
 * are null for panelless / legacy evaluations and expose nothing beyond the
 * already-returned sheet (no peer data); the read path that produces this DTO is
 * still gated by {@code PanelBiasGuard}, so a peer never reaches here for a
 * collecting-panel foreign sheet.
 */
public record EvaluationResponse(
        UUID id,
        UUID projectId,
        UUID positionId,
        UUID methodologyVersionId,
        UUID evaluatorUserId,
        UUID panelId,
        EvaluatorRole evaluatorRole,
        EvaluationStatus status,
        BigDecimal rawTotalScore,
        BigDecimal displayedTotalScore,
        UUID gradeBandId,
        Integer assignedGradeNumber,
        OffsetDateTime submittedAt,
        UUID submittedBy,
        OffsetDateTime approvedAt,
        UUID approvedBy,
        OffsetDateTime lockedAt,
        UUID lockedBy,
        OffsetDateTime archivedAt,
        UUID archivedBy
) {
    public static EvaluationResponse from(Evaluation e) {
        return new EvaluationResponse(
                e.id(), e.projectId(), e.positionId(), e.methodologyVersionId(),
                e.evaluatorUserId(), e.panelId(), e.evaluatorRole(),
                e.status(), e.rawTotalScore(), e.displayedTotalScore(),
                e.gradeBandId(), e.assignedGradeNumber(),
                e.submittedAt(), e.submittedBy(),
                e.approvedAt(), e.approvedBy(),
                e.lockedAt(), e.lockedBy(),
                e.archivedAt(), e.archivedBy());
    }
}
