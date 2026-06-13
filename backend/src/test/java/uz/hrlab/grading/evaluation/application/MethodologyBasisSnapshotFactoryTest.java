package uz.hrlab.grading.evaluation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.FactorLevel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE-3 — verifies the live snapshot JSON shape matches the changelog-040
 * backfill exactly: per factor {id, code, weight, max_points, required,
 * sort_order, levels[]}, per level {id, code, points, scale_value}; factors
 * ordered by sort_order then id; numeric fields emitted as JSON numbers.
 *
 * <p>Pure unit test — no Spring, no Docker.
 */
@Tag("scoring")
class MethodologyBasisSnapshotFactoryTest {

    private final MethodologyBasisSnapshotFactory factory =
            new MethodologyBasisSnapshotFactory(new ObjectMapper());

    private Factor factor(UUID id, String code, int sortOrder, BigDecimal weight) {
        return new Factor(id, UUID.randomUUID(), UUID.randomUUID(), code,
                Map.of("ru-RU", "Фактор"), Map.of(),
                weight, new BigDecimal("100.0000"), sortOrder, true);
    }

    private FactorLevel level(UUID id, UUID factorId, String code, int order, BigDecimal points) {
        return new FactorLevel(id, UUID.randomUUID(), factorId, code, order, points, null,
                Map.of("ru-RU", "Уровень"), Map.of());
    }

    @Test
    void buildsExpectedShapeAndOrdering() {
        UUID f1 = UUID.randomUUID();
        UUID f2 = UUID.randomUUID();
        // Provide factors out of sort order to assert the factory sorts them.
        Factor factorB = factor(f2, "B", 2, new BigDecimal("60.0000"));
        Factor factorA = factor(f1, "A", 1, new BigDecimal("40.0000"));
        UUID l1 = UUID.randomUUID();
        UUID l2 = UUID.randomUUID();
        Map<UUID, List<FactorLevel>> levels = Map.of(
                f1, List.of(level(l1, f1, "A1", 1, new BigDecimal("10.0000"))),
                f2, List.of(level(l2, f2, "B1", 1, new BigDecimal("20.0000"))));

        JsonNode snapshot = factory.build(List.of(factorB, factorA), levels);

        assertThat(snapshot.isArray()).isTrue();
        assertThat(snapshot).hasSize(2);

        JsonNode first = snapshot.get(0);
        // sort_order 1 (factor A) comes first.
        assertThat(first.get("code").asText()).isEqualTo("A");
        assertThat(first.get("id").asText()).isEqualTo(f1.toString());
        assertThat(first.get("weight").isNumber()).isTrue();
        assertThat(first.get("weight").decimalValue()).isEqualByComparingTo("40.0000");
        assertThat(first.get("max_points").isNumber()).isTrue();
        assertThat(first.get("required").asBoolean()).isTrue();
        assertThat(first.get("sort_order").asInt()).isEqualTo(1);
        assertThat(first.get("levels").isArray()).isTrue();
        JsonNode lvl = first.get("levels").get(0);
        assertThat(lvl.get("id").asText()).isEqualTo(l1.toString());
        assertThat(lvl.get("code").asText()).isEqualTo("A1");
        assertThat(lvl.get("points").isNumber()).isTrue();
        assertThat(lvl.get("scale_value").isNull()).isTrue();

        assertThat(snapshot.get(1).get("code").asText()).isEqualTo("B");
    }

    @Test
    void factorWithNoLevelsGetsEmptyArray() {
        UUID f1 = UUID.randomUUID();
        JsonNode snapshot = factory.build(
                List.of(factor(f1, "A", 1, new BigDecimal("100.0000"))), Map.of());
        assertThat(snapshot.get(0).get("levels").isArray()).isTrue();
        assertThat(snapshot.get(0).get("levels")).isEmpty();
    }
}
