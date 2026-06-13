package uz.hrlab.grading.evaluation.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE-3 — {@code setMethodologyBasisSnapshotIfAbsent} writes once and NEVER
 * overwrites. Guards the "subsequent factor edits / re-transition do not change
 * an already-captured snapshot" requirement at the entity invariant level.
 *
 * <p>Pure unit test — no Spring, no Docker.
 */
@Tag("scoring")
class EvaluationBasisSnapshotIdempotencyTest {

    private final ObjectMapper om = new ObjectMapper();

    private EvaluationJpaEntity evaluation() {
        return new EvaluationJpaEntity(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), EvaluationStatus.COMPLETE);
    }

    @Test
    void firstCallWritesSnapshot() {
        EvaluationJpaEntity e = evaluation();
        ArrayNode snap = om.createArrayNode();
        snap.add("factor-1");
        OffsetDateTime at = OffsetDateTime.now();

        e.setMethodologyBasisSnapshotIfAbsent(snap, at);

        assertThat(e.getMethodologyBasisSnapshot()).isEqualTo(snap);
        assertThat(e.getMethodologyBasisSnapshotAt()).isEqualTo(at);
    }

    @Test
    void secondCallDoesNotOverwrite() {
        EvaluationJpaEntity e = evaluation();
        ArrayNode original = om.createArrayNode();
        original.add("original-basis");
        OffsetDateTime firstAt = OffsetDateTime.now().minusDays(1);
        e.setMethodologyBasisSnapshotIfAbsent(original, firstAt);

        // A later factor edit / re-transition tries to write a DIFFERENT basis.
        ArrayNode mutated = om.createArrayNode();
        mutated.add("edited-basis");
        e.setMethodologyBasisSnapshotIfAbsent(mutated, OffsetDateTime.now());

        // The original frozen basis is preserved byte-for-byte.
        assertThat(e.getMethodologyBasisSnapshot()).isEqualTo(original);
        assertThat(e.getMethodologyBasisSnapshotAt()).isEqualTo(firstAt);
    }
}
