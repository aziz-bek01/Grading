package uz.hrlab.grading.evaluation.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BE-3 — pure averaging math. Pins the rounding contract (architecture §15.4:
 * BigDecimal HALF_UP, raw scale 4, displayed scale 2 — EXACT match to
 * {@link EvaluationScoringEngine}), denominator integrity (REQ-AVG-3 — only the
 * supplied COMPLETED sheets count), and reproducibility/purity (same input ->
 * byte-identical output).
 */
class PanelAveragingServiceTest {

    private final PanelAveragingService service = new PanelAveragingService();

    private static final UUID F1 = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID F2 = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
    private static final UUID F3 = UUID.fromString("00000000-0000-0000-0000-0000000000f3");

    private static EvaluatorFactorScores sheet(UUID evaluator, Map<UUID, BigDecimal> scores) {
        return new EvaluatorFactorScores(evaluator, scores);
    }

    private static Map<UUID, BigDecimal> scores(Object... kv) {
        Map<UUID, BigDecimal> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((UUID) kv[i], new BigDecimal(kv[i + 1].toString()));
        }
        return m;
    }

    // ---------------------------------------------------------------- mean math

    @Test
    void averagesPerFactorMeanWithScale4() {
        // F1: (10 + 20 + 30) / 3 = 20.0000 ; F2: (1 + 2 + 3) / 3 = 2.0000
        PanelAverageResult r = service.average(List.of(
                sheet(UUID.randomUUID(), scores(F1, "10", F2, "1")),
                sheet(UUID.randomUUID(), scores(F1, "20", F2, "2")),
                sheet(UUID.randomUUID(), scores(F1, "30", F2, "3"))));

        assertThat(r.evaluatorCount()).isEqualTo(3);
        assertThat(r.avgByFactor().get(F1)).isEqualByComparingTo("20.0000");
        assertThat(r.avgByFactor().get(F2)).isEqualByComparingTo("2.0000");
        // rawTotal = 20.0000 + 2.0000 = 22.0000 ; displayed = 22.00
        assertThat(r.rawTotal()).isEqualByComparingTo("22.0000");
        assertThat(r.displayedTotal()).isEqualByComparingTo("22.00");
        assertThat(r.rawTotal().scale()).isEqualTo(4);
        assertThat(r.displayedTotal().scale()).isEqualTo(2);
    }

    @Test
    void roundsHalfUpAtScale4PerFactor() {
        // F1: (10 + 10 + 11) / 3 = 31/3 = 10.33333... -> HALF_UP scale 4 = 10.3333
        PanelAverageResult r = service.average(List.of(
                sheet(UUID.randomUUID(), scores(F1, "10")),
                sheet(UUID.randomUUID(), scores(F1, "10")),
                sheet(UUID.randomUUID(), scores(F1, "11"))));

        assertThat(r.avgByFactor().get(F1)).isEqualByComparingTo("10.3333");
        assertThat(r.rawTotal()).isEqualByComparingTo("10.3333");
        assertThat(r.displayedTotal()).isEqualByComparingTo("10.33"); // HALF_UP 10.3333 -> 10.33
    }

    @Test
    void halfUpRoundsThirdDecimalUpwardOnDisplayed() {
        // F1: (10 + 10 + 12) / 3 = 32/3 = 10.66666... -> raw 10.6667 -> displayed 10.67
        PanelAverageResult r = service.average(List.of(
                sheet(UUID.randomUUID(), scores(F1, "10")),
                sheet(UUID.randomUUID(), scores(F1, "10")),
                sheet(UUID.randomUUID(), scores(F1, "12"))));

        assertThat(r.avgByFactor().get(F1)).isEqualByComparingTo("10.6667");
        assertThat(r.displayedTotal()).isEqualByComparingTo("10.67");
    }

    @Test
    void perFactorSumEqualsAverageOfTotalsWhenEveryEvaluatorScoredEveryFactor() {
        // Evaluator totals: 16, 24, 32 -> mean 24.  Per-factor sum-of-means must match.
        PanelAverageResult r = service.average(List.of(
                sheet(UUID.randomUUID(), scores(F1, "10", F2, "6")),
                sheet(UUID.randomUUID(), scores(F1, "14", F2, "10")),
                sheet(UUID.randomUUID(), scores(F1, "18", F2, "14"))));

        // F1 mean = 14, F2 mean = 10 -> total 24.
        assertThat(r.rawTotal()).isEqualByComparingTo("24.0000");
    }

    // ------------------------------------------------------ denominator (REQ-AVG-3)

    @Test
    void denominatorIsTheNumberOfSuppliedSheetsOnly() {
        // Caller passes only the 2 COMPLETED sheets; a withdrawn/incomplete sheet
        // is the caller's job to exclude — the service divides by the list size.
        PanelAverageResult r = service.average(List.of(
                sheet(UUID.randomUUID(), scores(F1, "10")),
                sheet(UUID.randomUUID(), scores(F1, "20"))));

        assertThat(r.evaluatorCount()).isEqualTo(2);
        assertThat(r.avgByFactor().get(F1)).isEqualByComparingTo("15.0000"); // 30/2
    }

    @Test
    void singleSheetAveragesToItself() {
        PanelAverageResult r = service.average(List.of(
                sheet(UUID.randomUUID(), scores(F1, "42.5", F2, "7.25"))));

        assertThat(r.evaluatorCount()).isEqualTo(1);
        assertThat(r.avgByFactor().get(F1)).isEqualByComparingTo("42.5000");
        assertThat(r.avgByFactor().get(F2)).isEqualByComparingTo("7.2500");
        assertThat(r.rawTotal()).isEqualByComparingTo("49.7500");
    }

    @Test
    void missingFactorOnOneSheetContributesZeroForThatSheet() {
        // Sheet 2 omits F2 -> F2 mean = (8 + 0) / 2 = 4.0000 (matches the engine's
        // unselected-factor semantics: an absent factor scores 0).
        PanelAverageResult r = service.average(List.of(
                sheet(UUID.randomUUID(), scores(F1, "10", F2, "8")),
                sheet(UUID.randomUUID(), scores(F1, "20"))));

        assertThat(r.avgByFactor().get(F1)).isEqualByComparingTo("15.0000"); // 30/2
        assertThat(r.avgByFactor().get(F2)).isEqualByComparingTo("4.0000");  // 8/2
        assertThat(r.rawTotal()).isEqualByComparingTo("19.0000");
    }

    @Test
    void nullScoreInsideASheetIsTreatedAsZero() {
        Map<UUID, BigDecimal> withNull = new LinkedHashMap<>();
        withNull.put(F1, new BigDecimal("12"));
        withNull.put(F2, null);
        PanelAverageResult r = service.average(List.of(
                sheet(UUID.randomUUID(), withNull),
                sheet(UUID.randomUUID(), scores(F1, "8", F2, "4"))));

        assertThat(r.avgByFactor().get(F1)).isEqualByComparingTo("10.0000"); // 20/2
        assertThat(r.avgByFactor().get(F2)).isEqualByComparingTo("2.0000");  // (0+4)/2
    }

    // -------------------------------------------------------------- determinism

    @Test
    void preservesFirstSeenFactorOrderForGoldenFileStability() {
        PanelAverageResult r = service.average(List.of(
                sheet(UUID.randomUUID(), scores(F3, "1", F1, "1", F2, "1"))));
        assertThat(r.avgByFactor().keySet()).containsExactly(F3, F1, F2);
    }

    @Test
    void sameInputProducesByteIdenticalOutput() {
        List<EvaluatorFactorScores> input = List.of(
                sheet(UUID.randomUUID(), scores(F1, "10", F2, "3")),
                sheet(UUID.randomUUID(), scores(F1, "11", F2, "4")),
                sheet(UUID.randomUUID(), scores(F1, "13", F2, "5")));

        PanelAverageResult a = service.average(input);
        PanelAverageResult b = service.average(input);

        assertThat(a.rawTotal()).isEqualByComparingTo(b.rawTotal());
        assertThat(a.displayedTotal()).isEqualByComparingTo(b.displayedTotal());
        assertThat(a.avgByFactor()).isEqualTo(b.avgByFactor());
        assertThat(a.evaluatorCount()).isEqualTo(b.evaluatorCount());
    }

    // -------------------------------------------------------------------- guards

    @Test
    void rejectsEmptySheetList() {
        assertThatThrownBy(() -> service.average(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullSheetList() {
        assertThatThrownBy(() -> service.average(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
