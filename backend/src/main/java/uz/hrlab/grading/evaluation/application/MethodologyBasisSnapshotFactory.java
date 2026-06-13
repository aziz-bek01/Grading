package uz.hrlab.grading.evaluation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.FactorLevel;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BE-3 — builds the {@code methodology_basis_snapshot} JSON for an evaluation
 * from its loaded factors + levels. The shape MUST match the changelog 040
 * backfill exactly so live and backfilled snapshots are structurally identical:
 *
 * <pre>
 * [ { "id","code","weight","max_points","required","sort_order",
 *     "levels": [ { "id","code","points","scale_value" }, ... ] }, ... ]
 * </pre>
 *
 * <p>Factors are ordered by sort_order then id; levels by level_order then id —
 * matching the 040 {@code ORDER BY} clauses — so the document is stable.
 * Numeric fields are emitted as JSON numbers (not strings) to mirror the
 * {@code jsonb_build_object} output of the SQL backfill.
 */
@Component
public class MethodologyBasisSnapshotFactory {

    private final ObjectMapper objectMapper;

    public MethodologyBasisSnapshotFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode build(List<Factor> factors, Map<UUID, List<FactorLevel>> levelsByFactor) {
        ArrayNode root = objectMapper.createArrayNode();
        List<Factor> orderedFactors = factors.stream()
                .sorted((a, b) -> {
                    int c = Integer.compare(a.sortOrder(), b.sortOrder());
                    return c != 0 ? c : a.id().compareTo(b.id());
                })
                .toList();
        for (Factor f : orderedFactors) {
            ObjectNode fo = objectMapper.createObjectNode();
            fo.put("id", f.id().toString());
            fo.put("code", f.code());
            putDecimal(fo, "weight", f.weight());
            putDecimal(fo, "max_points", f.maxPoints());
            fo.put("required", f.required());
            fo.put("sort_order", f.sortOrder());

            ArrayNode levelsArr = objectMapper.createArrayNode();
            List<FactorLevel> levels = levelsByFactor.get(f.id());
            if (levels != null) {
                levels.stream()
                        .sorted((a, b) -> {
                            int c = Integer.compare(a.levelOrder(), b.levelOrder());
                            return c != 0 ? c : a.id().compareTo(b.id());
                        })
                        .forEach(l -> {
                            ObjectNode lo = objectMapper.createObjectNode();
                            lo.put("id", l.id().toString());
                            lo.put("code", l.code());
                            putDecimal(lo, "points", l.points());
                            putDecimal(lo, "scale_value", l.scaleValue());
                            levelsArr.add(lo);
                        });
            }
            fo.set("levels", levelsArr);
            root.add(fo);
        }
        return root;
    }

    private static void putDecimal(ObjectNode node, String field, java.math.BigDecimal value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }
}
