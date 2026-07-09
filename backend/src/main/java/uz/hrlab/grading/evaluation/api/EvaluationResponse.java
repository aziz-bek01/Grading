package uz.hrlab.grading.evaluation.api;

import uz.hrlab.grading.evaluation.domain.Evaluation;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRowView;

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
 *
 * <p>FE-27 — {@code methodology_version_label} carries the server-resolved,
 * localized "Name (vN)" label for this evaluation's {@code methodology_version_id}
 * on the LIST read, so the client no longer fires one request per methodology to
 * resolve historical version labels. It is populated ONLY by the list path
 * ({@link #fromRow}); single-sheet reads leave it {@code null} (omitted on the wire
 * under the global NON_NULL inclusion).
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
        UUID archivedBy,
        String methodologyVersionLabel
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
                e.archivedAt(), e.archivedBy(),
                null);
    }

    /**
     * FE-27 / DB-23 — build a list row from the closed {@link EvaluationRowView}
     * projection (no methodology_basis_snapshot JSONB fetched), carrying the
     * pre-resolved {@code methodologyVersionLabel} so the client never resolves it.
     */
    public static EvaluationResponse fromRow(EvaluationRowView v, String methodologyVersionLabel) {
        return new EvaluationResponse(
                v.getId(), v.getProjectId(), v.getPositionId(), v.getMethodologyVersionId(),
                v.getEvaluatorUserId(), v.getPanelId(), v.getEvaluatorRole(),
                v.getStatus(), v.getRawTotalScore(), v.getDisplayedTotalScore(),
                v.getGradeBandId(), v.getAssignedGradeNumber(),
                v.getSubmittedAt(), v.getSubmittedBy(),
                v.getApprovedAt(), v.getApprovedBy(),
                v.getLockedAt(), v.getLockedBy(),
                v.getArchivedAt(), v.getArchivedBy(),
                methodologyVersionLabel);
    }
}
