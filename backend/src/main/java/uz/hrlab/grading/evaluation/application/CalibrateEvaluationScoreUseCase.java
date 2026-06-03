package uz.hrlab.grading.evaluation.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.EvaluationCalibrationEvent;
import uz.hrlab.grading.evaluation.domain.EvaluationImmutabilityPolicy;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationCalibrationEventJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationCalibrationEventRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Apply a manual adjustment to one factor's raw score on a SUBMITTED or
 * APPROVED evaluation. Pre-conditions:
 * <ul>
 *   <li>Permission {@code CALIBRATION_EDIT}.</li>
 *   <li>Evaluation status is SUBMITTED or APPROVED (LOCKED/ARCHIVED rejected
 *       by both {@link EvaluationImmutabilityPolicy} and the DB trigger).</li>
 *   <li>Reason length ≥ 20 chars (DB CHECK + app validation).</li>
 *   <li>Score row must already exist for the (evaluation, factor) pair —
 *       calibration adjusts an existing selection, never creates one.</li>
 * </ul>
 *
 * <p>Writes:
 * <ol>
 *   <li>Appends one {@link EvaluationCalibrationEventJpaEntity}.</li>
 *   <li>Updates {@link EvaluationScoreJpaEntity} with calibration metadata
 *       and the new raw score.</li>
 *   <li>Recomputes the evaluation's totals (preserving the adjusted value).</li>
 *   <li>Writes an {@code EVALUATION_SCORE_CALIBRATED} audit row.</li>
 * </ol>
 */
@Service
public class CalibrateEvaluationScoreUseCase {

    public static final int MIN_REASON_LENGTH = 20;

    private final EvaluationRepository evaluations;
    private final EvaluationScoreRepository scores;
    private final EvaluationCalibrationEventRepository calibrationEvents;
    private final EvaluationContextLoader loader;
    private final EvaluationRecomputeService recompute;
    private final EvaluationImmutabilityPolicy immutability;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final EvaluationAuditSnapshot snapshot;
    private final JdbcTemplate jdbcTemplate;
    private final EvaluationGradeAssignmentService gradeAssignment;

    public CalibrateEvaluationScoreUseCase(EvaluationRepository evaluations,
                                           EvaluationScoreRepository scores,
                                           EvaluationCalibrationEventRepository calibrationEvents,
                                           EvaluationContextLoader loader,
                                           EvaluationRecomputeService recompute,
                                           EvaluationImmutabilityPolicy immutability,
                                           AbacGate abacGate,
                                           AuditService audit,
                                           EvaluationAuditSnapshot snapshot,
                                           JdbcTemplate jdbcTemplate,
                                           EvaluationGradeAssignmentService gradeAssignment) {
        this.evaluations = evaluations;
        this.scores = scores;
        this.calibrationEvents = calibrationEvents;
        this.loader = loader;
        this.recompute = recompute;
        this.immutability = immutability;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
        this.jdbcTemplate = jdbcTemplate;
        this.gradeAssignment = gradeAssignment;
    }

    @Transactional
    public EvaluationCalibrationEvent calibrate(CalibrateEvaluationScoreCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.CALIBRATION_EDIT)) {
            throw new PermissionDeniedException();
        }
        if (cmd == null || cmd.evaluationId() == null || cmd.factorId() == null
                || cmd.newRawFactorScore() == null) {
            throw new ValidationException(
                    "evaluationId, factorId, and newRawFactorScore are required");
        }
        if (cmd.newRawFactorScore().signum() < 0) {
            throw new ValidationException("newRawFactorScore must be non-negative");
        }
        if (cmd.reason() == null || cmd.reason().trim().length() < MIN_REASON_LENGTH) {
            throw new ValidationException(
                    "reason is required (min " + MIN_REASON_LENGTH + " chars)");
        }
        EvaluationContext context = loader.load(cmd.evaluationId(), ctx.tenantId());
        EvaluationJpaEntity evaluation = context.evaluation();
        abacGate.enforceCanWriteInProject(ctx, evaluation.getProjectId());
        immutability.enforceCanCalibrate(evaluation.getStatus());

        EvaluationScoreJpaEntity row = scores
                .findByTenantIdAndEvaluationIdAndFactorId(
                        ctx.tenantId(), evaluation.getId(), cmd.factorId())
                .orElseThrow(() -> new TenantAccessDeniedException());

        BigDecimal newScore = cmd.newRawFactorScore().setScale(4, RoundingMode.HALF_UP);
        BigDecimal originalScore = row.getRawFactorScore() == null
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : row.getRawFactorScore();
        BigDecimal delta = newScore.subtract(originalScore).setScale(4, RoundingMode.HALF_UP);

        OffsetDateTime now = OffsetDateTime.now();
        // Persist calibration event first (append-only history).
        EvaluationCalibrationEventJpaEntity event = new EvaluationCalibrationEventJpaEntity(
                UUID.randomUUID(), ctx.tenantId(), evaluation.getId(), cmd.factorId(),
                originalScore, newScore, delta, cmd.reason(), ctx.userId(), now);
        calibrationEvents.save(event);

        // D-501: Set the transaction-local session variable that the DB
        // trigger `prevent_score_changes_on_locked_evaluation` checks before
        // allowing UPDATEs on APPROVED parent evaluations. `SET LOCAL`
        // automatically resets at end of transaction (commit/rollback) so the
        // flag cannot leak to other requests sharing the pooled connection.
        // Any caller that bypasses this use case will trigger the DB-level
        // EVALUATION_APPROVED_PROTECTED rejection.
        jdbcTemplate.execute("SET LOCAL app.calibration_in_progress = 'true'");

        // Update score row with calibration metadata.
        var beforeScoreJson = snapshot.of(row);
        row.setRawFactorScore(newScore);
        row.setManuallyAdjusted(true);
        // originalFactorScore — preserved from FIRST calibration, or set now if first.
        if (row.getOriginalFactorScore() == null) {
            row.setOriginalFactorScore(originalScore);
        }
        row.setAdjustedByUserId(ctx.userId());
        row.setAdjustedAt(now);
        row.setAdjustmentReason(cmd.reason());
        scores.save(row);

        // Recompute totals; calibrated value is preserved by recompute service.
        recompute.recompute(context);
        // Phase 6: if the evaluation is already APPROVED (calibration applies
        // post-approval too), re-run the grade lookup against the new total
        // score. GRADE_REASSIGNED audit event is emitted only if the band
        // changes; for SUBMITTED evaluations grade fields stay null until
        // approval.
        if (evaluation.getStatus() == uz.hrlab.grading.evaluation.domain.EvaluationStatus.APPROVED) {
            gradeAssignment.assignFromScore(evaluation, ctx.userId());
        }
        evaluations.save(evaluation);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(evaluation.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.EVALUATION_SCORE_CALIBRATED)
                .entityType("EvaluationScore")
                .entityId(row.getId())
                .reason(cmd.reason())
                .beforeJson(beforeScoreJson)
                .afterJson(snapshot.of(row))
                .build());
        return event.toDomain();
    }
}
