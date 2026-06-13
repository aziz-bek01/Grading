package uz.hrlab.grading.methodology.application;

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
}
