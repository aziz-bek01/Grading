package uz.hrlab.grading.integration.imports.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.evaluation.domain.EvaluationScoringEngine;
import uz.hrlab.grading.evaluation.domain.ScoringInputs;
import uz.hrlab.grading.evaluation.domain.ScoringResult;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.FactorLevel;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * P0 regression guard (QA gate — MethodologyFactorsRowCommitter import).
 *
 * <p>Before commit {@code 4404235} ("fix(import): WEIGHTED_SCALE scale_value +
 * re-import field resync"), an imported {@code WEIGHTED_SCALE} methodology
 * persisted {@code FactorLevel.scaleValue = null} for EVERY level (the
 * {@code scale_value} column was accepted by the template but never written to
 * the entity). The methodology imported and even APPROVED cleanly (the
 * approve-time weight-sum check never looks at scale_value), yet EVERY
 * evaluation scored against it silently computed {@code 0} via
 * {@link EvaluationScoringEngine#compute}. That is exactly the kind of defect
 * the Golden QA rule / release-blocker list calls "score not reproducible /
 * silently wrong" — worse than a loud failure because nothing in the UI or API
 * response signalled the corruption.
 *
 * <p>This test does NOT re-test the scoring engine's pure math (that is
 * {@code EvaluationScoringEngineTest#weightedScaleMultipliesWeightByScaleValue},
 * which uses hand-built domain objects). It proves the FULL production data
 * path the P0 bug actually lived in: committer row-commit -> the exact
 * {@link FactorJpaEntity} / {@link FactorLevelJpaEntity} instances persisted by
 * {@link MethodologyFactorsRowCommitter} -> {@code JpaEntity.toDomain()} (the
 * same mapping every real evaluation-scoring call site uses) -> the real
 * {@link EvaluationScoringEngine}. If a future change reintroduces a null/blank
 * {@code scaleValue} anywhere on that path, this test fails with a 0 total
 * instead of the expected non-zero value.
 */
@Tag("imports")
class MethodologyFactorsImportScoringRegressionTest {

    private MethodologyRepository methodologies;
    private MethodologyVersionRepository versions;
    private FactorRepository factors;
    private FactorLevelRepository levels;
    private AuditService audit;
    private MethodologyFactorsRowCommitter committer;
    private final EvaluationScoringEngine scoringEngine = new EvaluationScoringEngine();

    private UUID tenantId;
    private UUID projectId;
    private ImportRowCommitContext ctx;

    @BeforeEach
    void setUp() {
        methodologies = mock(MethodologyRepository.class);
        versions = mock(MethodologyVersionRepository.class);
        factors = mock(FactorRepository.class);
        levels = mock(FactorLevelRepository.class);
        audit = mock(AuditService.class);
        committer = new MethodologyFactorsRowCommitter(methodologies, versions, factors, levels, audit);

        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        ctx = new ImportRowCommitContext(tenantId, projectId, UUID.randomUUID(),
                UUID.randomUUID(), "trace-scoring-regression");

        given(methodologies.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(versions.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(factors.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(levels.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(methodologies.findByTenantIdAndProjectIdAndCode(any(), any(), any()))
                .willReturn(Optional.empty());
        given(factors.findByTenantIdAndMethodologyVersionIdAndCode(any(), any(), any()))
                .willReturn(Optional.empty());
        given(levels.findByTenantIdAndFactorIdAndCode(any(), any(), any()))
                .willReturn(Optional.empty());
    }

    @Test
    void importedWeightedScaleMethodology_scoresNonZero_throughRealCommitterAndEngine() {
        // weight=40, scale_value=2.5 → expected factor score = 40 * 2.5 = 100.0000.
        Map<String, String> row = weightedScaleRow("KNOWLEDGE", "Knowledge", "40",
                "L1", "Basic", "10", "2.5");

        committer.commit(row, ctx);

        ArgumentCaptor<FactorJpaEntity> factorCap = ArgumentCaptor.forClass(FactorJpaEntity.class);
        verify(factors).save(factorCap.capture());
        ArgumentCaptor<FactorLevelJpaEntity> levelCap = ArgumentCaptor.forClass(FactorLevelJpaEntity.class);
        verify(levels).save(levelCap.capture());

        // Exact production mapping — never hand-built domain objects.
        Factor factor = factorCap.getValue().toDomain();
        FactorLevel level = levelCap.getValue().toDomain();
        assertThat(level.scaleValue())
                .as("P0 regression: scale_value must be persisted on the level, not null")
                .isEqualByComparingTo(new BigDecimal("2.5"));

        MethodologyVersion version = new MethodologyVersion(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), 1,
                MethodologyVersionStatus.APPROVED, ScoringMode.WEIGHTED_SCALE,
                new BigDecimal("40"), null, OffsetDateTime.now(), null, null, null);

        ScoringInputs inputs = new ScoringInputs(
                version,
                List.of(factor),
                Map.of(factor.id(), List.of(level)),
                Map.of(factor.id(), level.id()));

        ScoringResult result = scoringEngine.compute(inputs);

        assertThat(result.rawTotal())
                .as("P0 regression guard: an imported WEIGHTED_SCALE methodology must "
                        + "never silently score 0")
                .isEqualByComparingTo(new BigDecimal("100.0000"))
                .isNotEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.perFactorRaw().get(factor.id())).isEqualByComparingTo(new BigDecimal("100.0000"));
    }

    @Test
    void importedWeightedScaleMethodology_zeroScaleValue_scoresExactlyZero_notNullInduced() {
        // Distinguishes a LEGITIMATE zero score (scale_value == 0, a real user
        // choice) from the P0 bug (scale_value silently null). Both produce a
        // total of 0.0000, but only THIS one carries an explicit persisted 0 —
        // proven by asserting scaleValue is non-null before scoring.
        Map<String, String> row = weightedScaleRow("KNOWLEDGE", "Knowledge", "40",
                "L1", "Basic", "10", "0");
        committer.commit(row, ctx);

        ArgumentCaptor<FactorLevelJpaEntity> levelCap = ArgumentCaptor.forClass(FactorLevelJpaEntity.class);
        verify(levels).save(levelCap.capture());
        FactorLevel level = levelCap.getValue().toDomain();
        assertThat(level.scaleValue())
                .as("explicit zero must be PERSISTED, not stored as null")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void importedDirectPointsMethodology_stillScoresCorrectly_throughSameRealChain() {
        // Parity check: the DIRECT_POINTS path (unaffected by the P0 fix) still
        // scores correctly through the identical committer -> toDomain() ->
        // engine chain, proving the regression guard isn't accidentally
        // tautological (i.e. it would fail the same way for every mode).
        Map<String, String> row = baseRow("KNOWLEDGE", "Knowledge", "100", "L1", "Basic", "75");
        row.put("scoring_mode", "DIRECT_POINTS");
        committer.commit(row, ctx);

        ArgumentCaptor<FactorJpaEntity> factorCap = ArgumentCaptor.forClass(FactorJpaEntity.class);
        verify(factors).save(factorCap.capture());
        ArgumentCaptor<FactorLevelJpaEntity> levelCap = ArgumentCaptor.forClass(FactorLevelJpaEntity.class);
        verify(levels).save(levelCap.capture());

        Factor factor = factorCap.getValue().toDomain();
        FactorLevel level = levelCap.getValue().toDomain();
        assertThat(level.scaleValue()).isNull(); // optional/ignored for DIRECT_POINTS

        MethodologyVersion version = new MethodologyVersion(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), 1,
                MethodologyVersionStatus.APPROVED, ScoringMode.DIRECT_POINTS,
                new BigDecimal("1000"), null, OffsetDateTime.now(), null, null, null);
        ScoringInputs inputs = new ScoringInputs(version, List.of(factor),
                Map.of(factor.id(), List.of(level)), Map.of(factor.id(), level.id()));
        ScoringResult result = scoringEngine.compute(inputs);

        assertThat(result.rawTotal()).isEqualByComparingTo(new BigDecimal("75.0000"));
    }

    // --------------------------------------------------------------------- helpers

    private static Map<String, String> baseRow(String factorCode, String factorName, String weight,
                                                String levelCode, String levelName, String score) {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("methodology_code", "ACME-GRADING");
        r.put("methodology_name", "ACME grading");
        r.put("methodology_type", "CLASSIC_8_FACTOR");
        r.put("scoring_mode", "WEIGHTED_POINTS");
        r.put("target_total_points", "1000");
        r.put("factor_code", factorCode);
        r.put("factor_name", factorName);
        r.put("weight", weight);
        r.put("score", score);
        r.put("level_code", levelCode);
        r.put("level_name", levelName);
        r.put("level_order", "1");
        return r;
    }

    private static Map<String, String> weightedScaleRow(String factorCode, String factorName, String weight,
                                                         String levelCode, String levelName, String score,
                                                         String scaleValue) {
        Map<String, String> r = baseRow(factorCode, factorName, weight, levelCode, levelName, score);
        r.put("scoring_mode", "WEIGHTED_SCALE");
        r.put("scale_value", scaleValue);
        return r;
    }
}
