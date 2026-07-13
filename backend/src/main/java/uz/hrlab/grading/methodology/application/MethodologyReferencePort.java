package uz.hrlab.grading.methodology.application;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Outbound port (hexagonal boundary) the methodology module uses to consult the
 * evaluation module WITHOUT importing it — the static dependency direction stays
 * evaluation → methodology. The implementation
 * ({@code EvaluationMethodologyReferenceAdapter}) lives in the evaluation module
 * and is injected by Spring.
 *
 * <p>Used by the approved-edit factor / level write paths (BE-4 delete
 * protection, BE-5 audit blast-radius) to decide hard-delete vs soft-deprecate
 * and to stamp the frozen-evaluation count.
 */
public interface MethodologyReferencePort {

    /**
     * True when any {@code evaluation_scores} or
     * {@code evaluation_calibration_events} row references this factor
     * (tenant-scoped). Such a factor is preserved historically — on an APPROVED
     * version it is soft-deprecated rather than deleted.
     */
    boolean isFactorReferenced(UUID tenantId, UUID factorId);

    /**
     * True when any {@code evaluation_scores} row references this factor level
     * (tenant-scoped). Calibration events reference a factor, not a level, so
     * only scores are consulted here.
     */
    boolean isFactorLevelReferenced(UUID tenantId, UUID factorLevelId);

    /**
     * Number of NON-archived evaluations pinned to this methodology version
     * (tenant-scoped) — the blast radius stamped on the
     * {@code METHODOLOGY_APPROVED_EDIT} umbrella audit event (BE-5).
     */
    long countNonArchivedEvaluationsPinnedToVersion(UUID tenantId, UUID methodologyVersionId);

    /**
     * Distinct methodology-version ids the given evaluator has at least one OWN,
     * NON-archived evaluation under, within a project (tenant + project + evaluator
     * pinned). Backs the evaluator-scoped methodology list
     * ({@code GET /api/v1/methodologies/my}). The ARCHIVED exclusion is an
     * evaluation-domain concern owned by the adapter, so the methodology module
     * never needs the {@code EvaluationStatus} enum.
     */
    List<UUID> findNonArchivedEvaluationVersionIdsForEvaluator(
            UUID tenantId, UUID projectId, UUID evaluatorUserId);

    /**
     * Count of IN-PROGRESS (pre-submission: DRAFT / INCOMPLETE / COMPLETE)
     * evaluations across a set of methodology versions (tenant-scoped). Backs the
     * deactivate/archive confirmation dialog. The pre-submission status set is an
     * evaluation-domain concern owned by the adapter (single source of truth:
     * {@code EvaluationStatus.isPreSubmission}).
     */
    long countInProgressEvaluationsForVersions(
            UUID tenantId, Collection<UUID> methodologyVersionIds);
}
