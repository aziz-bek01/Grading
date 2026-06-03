package uz.hrlab.grading.evaluation.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.FactorLevel;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.ScoringMode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture §15.2/§15.3/§15.4 — verifies all three scoring modes against
 * known fixtures with exact 4-decimal BigDecimal equality.
 *
 * <p>Reproducibility (PRD Phase 5 AC): same inputs twice → byte-identical
 * BigDecimal output (uses {@code .equals()} to catch scale differences, not
 * just {@code .compareTo()}).
 */
@Tag("workflow")
class EvaluationScoringEngineTest {

    private final EvaluationScoringEngine engine = new EvaluationScoringEngine();
    private final UUID tenant = UUID.randomUUID();
    private final UUID methodology = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();

    @Test
    void directPointsSumOfLevelPoints() {
        // 3 factors, each level = 100 / 200 / 300 → total 600
        Factor a = factor("A", "100", "100", 1, true);
        Factor b = factor("B", "100", "100", 2, true);
        Factor c = factor("C", "100", "100", 3, true);
        FactorLevel la = level(a.id(), "L", "100", null);
        FactorLevel lb = level(b.id(), "L", "200", null);
        FactorLevel lc = level(c.id(), "L", "300", null);

        ScoringInputs inputs = new ScoringInputs(
                version(ScoringMode.DIRECT_POINTS),
                List.of(a, b, c),
                Map.of(a.id(), List.of(la), b.id(), List.of(lb), c.id(), List.of(lc)),
                selections(a.id(), la.id(), b.id(), lb.id(), c.id(), lc.id()));

        ScoringResult r = engine.compute(inputs);
        assertThat(r.rawTotal()).isEqualTo(new BigDecimal("600.0000"));
        assertThat(r.displayedTotal()).isEqualTo(new BigDecimal("600.00"));
        assertThat(r.perFactorRaw().get(a.id())).isEqualTo(new BigDecimal("100.0000"));
        assertThat(r.perFactorRaw().get(b.id())).isEqualTo(new BigDecimal("200.0000"));
        assertThat(r.perFactorRaw().get(c.id())).isEqualTo(new BigDecimal("300.0000"));
    }

    @Test
    void weightedPointsNormalizesByMaxPoints() {
        // weight=40, maxPoints=100, selected level=50 → 40 * (50/100) = 20.0000
        Factor f = factor("F", "40", "100", 1, true);
        FactorLevel l = level(f.id(), "L1", "50", null);

        ScoringInputs inputs = new ScoringInputs(
                version(ScoringMode.WEIGHTED_POINTS),
                List.of(f),
                Map.of(f.id(), List.of(l)),
                selections(f.id(), l.id()));
        ScoringResult r = engine.compute(inputs);
        assertThat(r.rawTotal()).isEqualTo(new BigDecimal("20.0000"));
        assertThat(r.displayedTotal()).isEqualTo(new BigDecimal("20.00"));
    }

    @Test
    void weightedScaleMultipliesWeightByScaleValue() {
        // weight=10, scaleValue=2.5 → 25.0000
        Factor f = factor("F", "10", "0", 1, true);
        FactorLevel l = level(f.id(), "L1", "0", "2.5");

        ScoringInputs inputs = new ScoringInputs(
                version(ScoringMode.WEIGHTED_SCALE),
                List.of(f),
                Map.of(f.id(), List.of(l)),
                selections(f.id(), l.id()));
        ScoringResult r = engine.compute(inputs);
        assertThat(r.rawTotal()).isEqualTo(new BigDecimal("25.0000"));
    }

    @Test
    void unselectedFactorContributesZero() {
        Factor f = factor("F", "100", "100", 1, false);
        FactorLevel l = level(f.id(), "L", "50", null);

        ScoringInputs inputs = new ScoringInputs(
                version(ScoringMode.DIRECT_POINTS),
                List.of(f),
                Map.of(f.id(), List.of(l)),
                Map.of()); // empty selections
        ScoringResult r = engine.compute(inputs);
        assertThat(r.rawTotal()).isEqualTo(new BigDecimal("0.0000"));
        assertThat(r.perFactorRaw().get(f.id())).isEqualTo(new BigDecimal("0.0000"));
    }

    @Test
    void weightedPointsZeroMaxYieldsZero() {
        // Defence: maxPoints=0 must not throw division-by-zero.
        Factor f = factor("F", "40", "0", 1, true);
        FactorLevel l = level(f.id(), "L", "50", null);
        ScoringInputs inputs = new ScoringInputs(
                version(ScoringMode.WEIGHTED_POINTS),
                List.of(f),
                Map.of(f.id(), List.of(l)),
                selections(f.id(), l.id()));
        ScoringResult r = engine.compute(inputs);
        assertThat(r.rawTotal()).isEqualTo(new BigDecimal("0.0000"));
    }

    @Test
    void reproducibilitySameInputsTwiceProducesByteIdenticalOutput() {
        Factor a = factor("A", "33.3333", "100", 1, true);
        Factor b = factor("B", "33.3333", "100", 2, true);
        Factor c = factor("C", "33.3334", "100", 3, true);
        FactorLevel la = level(a.id(), "L", "75", null);
        FactorLevel lb = level(b.id(), "L", "50", null);
        FactorLevel lc = level(c.id(), "L", "100", null);

        ScoringInputs inputs = new ScoringInputs(
                version(ScoringMode.WEIGHTED_POINTS),
                List.of(a, b, c),
                Map.of(a.id(), List.of(la), b.id(), List.of(lb), c.id(), List.of(lc)),
                selections(a.id(), la.id(), b.id(), lb.id(), c.id(), lc.id()));

        ScoringResult r1 = engine.compute(inputs);
        ScoringResult r2 = engine.compute(inputs);

        // .equals() catches scale differences — compareTo would not.
        assertThat(r1.rawTotal()).isEqualTo(r2.rawTotal());
        assertThat(r1.displayedTotal()).isEqualTo(r2.displayedTotal());
        assertThat(r1.perFactorRaw()).isEqualTo(r2.perFactorRaw());
        // Exact precision check at scale 4 — bit-by-bit.
        assertThat(r1.rawTotal().scale()).isEqualTo(4);
        assertThat(r1.displayedTotal().scale()).isEqualTo(2);
        // 33.3333 * (75/100) + 33.3333 * (50/100) + 33.3334 * (100/100)
        // = 24.999975 + 16.666650 + 33.3334 = 74.999725 → rounded HALF_UP = 75.0000
        // (per-factor: 25.0000 (33.3333*0.75=24.999975 → HALF_UP 4dp = 25.0000? No, 24.999975 rounds to 25.0000)
        // Verifying total to 4dp HALF_UP:
        // factor sums to 4dp each then total: 25.0000 + 16.6667 + 33.3334 = 75.0001
        assertThat(r1.rawTotal()).isEqualTo(new BigDecimal("75.0001"));
    }

    @Test
    void boundaryWeightsThatDoNotDivideEvenly() {
        // 3 factors weighted 33.3333% each, all level 100 / max 100 → factor=33.3333
        // total = 99.9999 (not 100; rounding boundary).
        Factor a = factor("A", "33.3333", "100", 1, true);
        Factor b = factor("B", "33.3333", "100", 2, true);
        Factor c = factor("C", "33.3333", "100", 3, true);
        FactorLevel la = level(a.id(), "L", "100", null);
        FactorLevel lb = level(b.id(), "L", "100", null);
        FactorLevel lc = level(c.id(), "L", "100", null);
        ScoringInputs inputs = new ScoringInputs(
                version(ScoringMode.WEIGHTED_POINTS),
                List.of(a, b, c),
                Map.of(a.id(), List.of(la), b.id(), List.of(lb), c.id(), List.of(lc)),
                selections(a.id(), la.id(), b.id(), lb.id(), c.id(), lc.id()));
        ScoringResult r = engine.compute(inputs);
        assertThat(r.rawTotal()).isEqualTo(new BigDecimal("99.9999"));
        assertThat(r.displayedTotal()).isEqualTo(new BigDecimal("100.00"));
    }

    // helpers -----------------------------------------------------------

    private MethodologyVersion version(ScoringMode mode) {
        return new MethodologyVersion(
                versionId, tenant, methodology, 1,
                MethodologyVersionStatus.APPROVED, mode,
                new BigDecimal("100"), null,
                OffsetDateTime.now(), null, null, null);
    }

    private Factor factor(String code, String weight, String maxPoints, int sortOrder,
                          boolean required) {
        return new Factor(UUID.randomUUID(), tenant, versionId, code,
                Map.of("ru-RU", code), Map.of(),
                new BigDecimal(weight), new BigDecimal(maxPoints), sortOrder, required);
    }

    private FactorLevel level(UUID factorId, String code, String points, String scaleValue) {
        return new FactorLevel(UUID.randomUUID(), tenant, factorId, code, 1,
                new BigDecimal(points),
                scaleValue == null ? null : new BigDecimal(scaleValue),
                Map.of("ru-RU", code), Map.of());
    }

    private static Map<UUID, UUID> selections(UUID... pairs) {
        Map<UUID, UUID> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put(pairs[i], pairs[i + 1]);
        }
        return out;
    }
}
