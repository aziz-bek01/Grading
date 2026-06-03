package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.EvaluationCalibrationEvent;
import uz.hrlab.grading.evaluation.domain.EvaluationImmutabilityPolicy;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationTransitionRejectedException;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationCalibrationEventJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationCalibrationEventRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-505 — Mockito unit test for {@link CalibrateEvaluationScoreUseCase}.
 *
 * <p>Scenarios covered:
 * <ol>
 *   <li>APPROVED evaluation → calibrate succeeds; EvaluationCalibrationEvent saved
 *       with original=preCalibrationScore, adjusted=newScore, delta=adjusted-original,
 *       decidedBy=actor, decidedAt non-null.</li>
 *   <li>SUBMITTED evaluation → calibration succeeds (allowed by policy).</li>
 *   <li>DRAFT evaluation → calibration rejected by {@link EvaluationImmutabilityPolicy}.</li>
 *   <li>Reason shorter than 20 chars → ValidationException.</li>
 *   <li>Wrong factorId (no score row exists) → TenantAccessDeniedException.</li>
 *   <li>Missing CALIBRATION_EDIT permission → PermissionDeniedException.</li>
 *   <li>Total score recomputed after calibration (recompute called exactly once).</li>
 *   <li>Multi-event: calibrating the same factor twice in sequence — the score
 *       row's original_factor_score remains the FIRST pre-calibration value
 *       across both events (the use case's
 *       {@code if (row.getOriginalFactorScore() == null)} guard).</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class CalibrateEvaluationScoreUseCaseTest {

    @Mock EvaluationRepository evaluations;
    @Mock EvaluationScoreRepository scores;
    @Mock EvaluationCalibrationEventRepository calibrationEvents;
    @Mock EvaluationContextLoader loader;
    @Mock EvaluationRecomputeService recompute;
    @Mock AbacGate abacGate;
    @Mock AuditService audit;
    @Mock EvaluationAuditSnapshot snapshot;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock EvaluationGradeAssignmentService gradeAssignment;

    EvaluationImmutabilityPolicy immutability = new EvaluationImmutabilityPolicy();

    CalibrateEvaluationScoreUseCase useCase;

    UUID tenantId;
    UUID userId;
    UUID projectId;
    UUID evaluationId;
    UUID factorId;
    UUID scoreRowId;

    @BeforeEach
    void setUp() {
        useCase = new CalibrateEvaluationScoreUseCase(
                evaluations, scores, calibrationEvents, loader, recompute,
                immutability, abacGate, audit, snapshot, jdbcTemplate, gradeAssignment);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        evaluationId = UUID.randomUUID();
        factorId = UUID.randomUUID();
        scoreRowId = UUID.randomUUID();

        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("CALIBRATION_EDIT"),
                Set.of(), false, "ru-RU"));
    }

    @AfterEach
    void tearDown() { TenantContextHolder.clear(); }

    @Test
    void calibrateOnApprovedEvaluationSucceedsAndPersistsHistory() {
        EvaluationJpaEntity evaluation = stubEvaluation(EvaluationStatus.APPROVED);
        EvaluationScoreJpaEntity row = stubScoreRow(new BigDecimal("50.0000"));
        when(loader.load(evaluationId, tenantId)).thenReturn(stubContext(evaluation));
        when(scores.findByTenantIdAndEvaluationIdAndFactorId(tenantId, evaluationId, factorId))
                .thenReturn(Optional.of(row));
        doNothing().when(abacGate).enforceCanWriteInProject(any(), eq(projectId));

        EvaluationCalibrationEvent result = useCase.calibrate(new CalibrateEvaluationScoreCommand(
                evaluationId, factorId, new BigDecimal("70"),
                "Calibration adjustment per committee decision"));

        assertThat(result).isNotNull();
        // jdbcTemplate.execute called with SET LOCAL session flag (D-501 contract).
        verify(jdbcTemplate).execute(eq("SET LOCAL app.calibration_in_progress = 'true'"));

        ArgumentCaptor<EvaluationCalibrationEventJpaEntity> eventCaptor =
                ArgumentCaptor.forClass(EvaluationCalibrationEventJpaEntity.class);
        verify(calibrationEvents).save(eventCaptor.capture());
        EvaluationCalibrationEventJpaEntity saved = eventCaptor.getValue();
        assertThat(saved.getOriginalScore()).isEqualByComparingTo(new BigDecimal("50.0000"));
        assertThat(saved.getAdjustedScore()).isEqualByComparingTo(new BigDecimal("70.0000"));
        assertThat(saved.getDelta()).isEqualByComparingTo(new BigDecimal("20.0000"));
        assertThat(saved.getDecidedBy()).isEqualTo(userId);
        assertThat(saved.getDecidedAt()).isNotNull();
        assertThat(saved.getEvaluationId()).isEqualTo(evaluationId);
        assertThat(saved.getFactorId()).isEqualTo(factorId);

        // Score row was updated with calibration metadata.
        assertThat(row.isManuallyAdjusted()).isTrue();
        assertThat(row.getRawFactorScore()).isEqualByComparingTo(new BigDecimal("70.0000"));
        assertThat(row.getOriginalFactorScore()).isEqualByComparingTo(new BigDecimal("50.0000"));
        assertThat(row.getAdjustedByUserId()).isEqualTo(userId);
        assertThat(row.getAdjustmentReason())
                .isEqualTo("Calibration adjustment per committee decision");
        verify(scores).save(row);

        // Recompute (totals re-derived) called exactly once.
        verify(recompute, times(1)).recompute(any(EvaluationContext.class));
        verify(evaluations, times(1)).save(evaluation);

        // Audit row written with EVALUATION_SCORE_CALIBRATED.
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action())
                .isEqualTo("EVALUATION_SCORE_CALIBRATED");
    }

    @Test
    void calibrateOnSubmittedEvaluationIsAllowed() {
        EvaluationJpaEntity evaluation = stubEvaluation(EvaluationStatus.SUBMITTED);
        EvaluationScoreJpaEntity row = stubScoreRow(new BigDecimal("40.0000"));
        when(loader.load(evaluationId, tenantId)).thenReturn(stubContext(evaluation));
        when(scores.findByTenantIdAndEvaluationIdAndFactorId(tenantId, evaluationId, factorId))
                .thenReturn(Optional.of(row));
        doNothing().when(abacGate).enforceCanWriteInProject(any(), eq(projectId));

        useCase.calibrate(new CalibrateEvaluationScoreCommand(
                evaluationId, factorId, new BigDecimal("55"),
                "Initial submission calibration tweak"));
        verify(calibrationEvents, times(1)).save(any());
    }

    @Test
    void calibrateOnDraftEvaluationIsRejectedByImmutabilityPolicy() {
        EvaluationJpaEntity evaluation = stubEvaluation(EvaluationStatus.DRAFT);
        when(loader.load(evaluationId, tenantId)).thenReturn(stubContext(evaluation));
        doNothing().when(abacGate).enforceCanWriteInProject(any(), eq(projectId));

        assertThatThrownBy(() -> useCase.calibrate(new CalibrateEvaluationScoreCommand(
                evaluationId, factorId, new BigDecimal("99"),
                "Cannot calibrate a DRAFT evaluation explanation")))
                .isInstanceOf(EvaluationTransitionRejectedException.class);
        verify(calibrationEvents, never()).save(any());
        // SET LOCAL must NOT be issued for a rejected calibration.
        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void calibrateOnLockedEvaluationIsRejected() {
        EvaluationJpaEntity evaluation = stubEvaluation(EvaluationStatus.LOCKED);
        when(loader.load(evaluationId, tenantId)).thenReturn(stubContext(evaluation));
        doNothing().when(abacGate).enforceCanWriteInProject(any(), eq(projectId));

        assertThatThrownBy(() -> useCase.calibrate(new CalibrateEvaluationScoreCommand(
                evaluationId, factorId, new BigDecimal("99"),
                "Should not be able to calibrate LOCKED evaluation")))
                .isInstanceOf(EvaluationTransitionRejectedException.class);
    }

    @Test
    void reasonShorterThan20CharsIsRejected() {
        assertThatThrownBy(() -> useCase.calibrate(new CalibrateEvaluationScoreCommand(
                evaluationId, factorId, new BigDecimal("70"), "too short")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("min " + CalibrateEvaluationScoreUseCase.MIN_REASON_LENGTH);
        verify(loader, never()).load(any(), any());
        verify(calibrationEvents, never()).save(any());
    }

    @Test
    void nullReasonIsRejected() {
        assertThatThrownBy(() -> useCase.calibrate(new CalibrateEvaluationScoreCommand(
                evaluationId, factorId, new BigDecimal("70"), null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void negativeNewScoreIsRejected() {
        assertThatThrownBy(() -> useCase.calibrate(new CalibrateEvaluationScoreCommand(
                evaluationId, factorId, new BigDecimal("-1"),
                "Negative score must be rejected by validation")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void missingScoreRowForFactorIsRejected() {
        EvaluationJpaEntity evaluation = stubEvaluation(EvaluationStatus.APPROVED);
        when(loader.load(evaluationId, tenantId)).thenReturn(stubContext(evaluation));
        when(scores.findByTenantIdAndEvaluationIdAndFactorId(tenantId, evaluationId, factorId))
                .thenReturn(Optional.empty());
        doNothing().when(abacGate).enforceCanWriteInProject(any(), eq(projectId));

        assertThatThrownBy(() -> useCase.calibrate(new CalibrateEvaluationScoreCommand(
                evaluationId, factorId, new BigDecimal("70"),
                "Wrong factor id should produce 404-equivalent")))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void missingCalibrationEditPermissionIsRejected() {
        TenantContextHolder.clear();
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("EVALUATION_EDIT"),  // no CALIBRATION_EDIT
                Set.of(), false, "ru-RU"));

        assertThatThrownBy(() -> useCase.calibrate(new CalibrateEvaluationScoreCommand(
                evaluationId, factorId, new BigDecimal("70"),
                "Should be rejected without CALIBRATION_EDIT permission")))
                .isInstanceOf(PermissionDeniedException.class);
        verify(loader, never()).load(any(), any());
    }

    @Test
    void multipleCalibrationsPreserveFirstOriginalFactorScore() {
        // D-505 invariant: the row's original_factor_score is captured on the
        // FIRST calibration only; subsequent calibrations leave it unchanged.
        EvaluationJpaEntity evaluation = stubEvaluation(EvaluationStatus.APPROVED);
        EvaluationScoreJpaEntity row = stubScoreRow(new BigDecimal("50.0000"));
        when(loader.load(evaluationId, tenantId)).thenReturn(stubContext(evaluation));
        when(scores.findByTenantIdAndEvaluationIdAndFactorId(tenantId, evaluationId, factorId))
                .thenReturn(Optional.of(row));
        doNothing().when(abacGate).enforceCanWriteInProject(any(), eq(projectId));

        // Call 1: 50 -> 70. original=50 captured.
        useCase.calibrate(new CalibrateEvaluationScoreCommand(
                evaluationId, factorId, new BigDecimal("70"),
                "First calibration anchors the original score"));
        assertThat(row.getOriginalFactorScore())
                .isEqualByComparingTo(new BigDecimal("50.0000"));
        assertThat(row.getRawFactorScore())
                .isEqualByComparingTo(new BigDecimal("70.0000"));

        // Call 2: 70 -> 80. originalFactorScore must STILL equal 50, not 70.
        useCase.calibrate(new CalibrateEvaluationScoreCommand(
                evaluationId, factorId, new BigDecimal("80"),
                "Second calibration must not reset the original score"));
        assertThat(row.getOriginalFactorScore())
                .as("originalFactorScore unchanged across multiple calibrations")
                .isEqualByComparingTo(new BigDecimal("50.0000"));
        assertThat(row.getRawFactorScore())
                .isEqualByComparingTo(new BigDecimal("80.0000"));

        // Two event rows saved.
        ArgumentCaptor<EvaluationCalibrationEventJpaEntity> eventCaptor =
                ArgumentCaptor.forClass(EvaluationCalibrationEventJpaEntity.class);
        verify(calibrationEvents, times(2)).save(eventCaptor.capture());
        List<EvaluationCalibrationEventJpaEntity> saved = eventCaptor.getAllValues();
        // First event: original=50, adjusted=70.
        assertThat(saved.get(0).getOriginalScore())
                .isEqualByComparingTo(new BigDecimal("50.0000"));
        assertThat(saved.get(0).getAdjustedScore())
                .isEqualByComparingTo(new BigDecimal("70.0000"));
        // Second event: original=70 (preCall value), adjusted=80. The event's
        // "original" is the immediate pre-call value (per-call delta); the
        // anchored "first original" lives on the score row itself.
        assertThat(saved.get(1).getOriginalScore())
                .isEqualByComparingTo(new BigDecimal("70.0000"));
        assertThat(saved.get(1).getAdjustedScore())
                .isEqualByComparingTo(new BigDecimal("80.0000"));

        // jdbcTemplate.execute called once per legitimate calibration call.
        verify(jdbcTemplate, times(2))
                .execute(eq("SET LOCAL app.calibration_in_progress = 'true'"));

        // Recompute called twice (one per calibration).
        verify(recompute, times(2)).recompute(any(EvaluationContext.class));
    }

    // ---------- helpers ----------

    private EvaluationJpaEntity stubEvaluation(EvaluationStatus status) {
        EvaluationJpaEntity e = new EvaluationJpaEntity(
                evaluationId, tenantId, projectId,
                UUID.randomUUID(), UUID.randomUUID(), userId, status);
        return e;
    }

    private EvaluationScoreJpaEntity stubScoreRow(BigDecimal score) {
        EvaluationScoreJpaEntity row = new EvaluationScoreJpaEntity(
                scoreRowId, tenantId, evaluationId, factorId, UUID.randomUUID(),
                score.setScale(4, RoundingMode.HALF_UP));
        return row;
    }

    private EvaluationContext stubContext(EvaluationJpaEntity evaluation) {
        Factor factor = new Factor(factorId, tenantId, UUID.randomUUID(), "F1",
                Map.of("ru-RU", "F1"), null,
                new BigDecimal("100"), new BigDecimal("100"), 1, true);
        return new EvaluationContext(evaluation, null, List.of(factor), Map.of(factorId, List.of()));
    }
}
