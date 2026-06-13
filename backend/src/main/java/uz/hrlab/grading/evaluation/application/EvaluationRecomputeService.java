package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.evaluation.domain.EvaluationCompletenessChecker;
import uz.hrlab.grading.evaluation.domain.EvaluationScoringEngine;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.domain.ScoringInputs;
import uz.hrlab.grading.evaluation.domain.ScoringResult;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.FactorLevel;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recompute helper: rebuilds the {@link ScoringInputs} from the persisted
 * scores, runs {@link EvaluationScoringEngine}, persists totals + adjusts
 * {@link EvaluationStatus} (DRAFT/INCOMPLETE/COMPLETE) via
 * {@link EvaluationCompletenessChecker}.
 *
 * <p>Used by both the regular score upsert path and the calibration path so
 * the engine is invoked in exactly one place.
 */
@Component
public class EvaluationRecomputeService {

    private final EvaluationScoreRepository scores;
    private final EvaluationScoringEngine engine;
    private final EvaluationCompletenessChecker completeness;
    private final MethodologyBasisSnapshotFactory basisSnapshotFactory;

    public EvaluationRecomputeService(EvaluationScoreRepository scores,
                                      EvaluationScoringEngine engine,
                                      EvaluationCompletenessChecker completeness,
                                      MethodologyBasisSnapshotFactory basisSnapshotFactory) {
        this.scores = scores;
        this.engine = engine;
        this.completeness = completeness;
        this.basisSnapshotFactory = basisSnapshotFactory;
    }

    /**
     * Recompute totals + status from persisted scores. Returns the scoring
     * result for downstream auditing.
     *
     * <p>If the evaluation has a manually-adjusted score, the engine's value
     * for that factor is replaced by the persisted {@code rawFactorScore} —
     * we never overwrite a calibration in a recompute.
     */
    public ScoringResult recompute(EvaluationContext ctx) {
        EvaluationJpaEntity e = ctx.evaluation();
        List<EvaluationScoreJpaEntity> rows = scores
                .findAllByTenantIdAndEvaluationId(e.getTenantId(), e.getId());

        Map<UUID, UUID> selections = new LinkedHashMap<>();
        Map<UUID, java.math.BigDecimal> overrides = new HashMap<>();
        for (EvaluationScoreJpaEntity row : rows) {
            selections.put(row.getFactorId(), row.getFactorLevelId());
            if (row.isManuallyAdjusted()) {
                overrides.put(row.getFactorId(), row.getRawFactorScore());
            }
        }
        ScoringResult engineResult = engine.compute(ctx.toScoringInputs(selections));

        // Apply calibration overrides + recompute totals.
        Map<UUID, java.math.BigDecimal> perFactor = new LinkedHashMap<>(engineResult.perFactorRaw());
        java.math.BigDecimal total = java.math.BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP);
        for (Factor f : ctx.factors()) {
            java.math.BigDecimal value = overrides.getOrDefault(f.id(), perFactor.get(f.id()));
            if (value == null) {
                value = java.math.BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP);
            }
            perFactor.put(f.id(), value);
            total = total.add(value);
        }
        java.math.BigDecimal rawTotal = total.setScale(4, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal displayed = rawTotal.setScale(2, java.math.RoundingMode.HALF_UP);

        // Persist updated row factor scores so engine + override line up.
        for (EvaluationScoreJpaEntity row : rows) {
            if (row.isManuallyAdjusted()) continue;
            java.math.BigDecimal newScore = perFactor.get(row.getFactorId());
            if (newScore != null && row.getRawFactorScore().compareTo(newScore) != 0) {
                row.setRawFactorScore(newScore);
                scores.save(row);
            }
        }

        // Recompute status (DRAFT/INCOMPLETE/COMPLETE only; immutable statuses untouched).
        EvaluationStatus newStatus = completeness.recomputeStatus(
                e.getStatus(), ctx.factors(), selections.keySet());

        e.setRawTotalScore(rawTotal);
        e.setDisplayedTotalScore(displayed);
        if (newStatus != e.getStatus()) {
            e.setStatus(newStatus);
        }

        // BE-3 — the first time the evaluation has a complete scorable basis,
        // freeze the methodology basis (factors + levels) onto the row. Captured
        // from the loaded context (same factors used to score), in the exact
        // changelog-040 shape. setMethodologyBasisSnapshotIfAbsent is idempotent:
        // an existing snapshot (live or backfilled) is NEVER overwritten, so a
        // later factor edit or a COMPLETE→...→COMPLETE re-transition leaves the
        // frozen basis untouched.
        if (newStatus == EvaluationStatus.COMPLETE) {
            e.setMethodologyBasisSnapshotIfAbsent(
                    basisSnapshotFactory.build(ctx.factors(), ctx.levelsByFactor()),
                    java.time.OffsetDateTime.now());
        }
        return new ScoringResult(rawTotal, displayed, perFactor);
    }

    /**
     * Pure preview — does NOT touch the database, does NOT modify any entity.
     * Used by the preview endpoint to compute hypothetical totals.
     */
    public ScoringResult preview(EvaluationContext ctx, Map<UUID, UUID> selections) {
        return engine.compute(ctx.toScoringInputs(selections));
    }
}
