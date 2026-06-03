package uz.hrlab.grading.gradestructure.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Validates that a set of {@link GradeBand}s belonging to the same
 * {@link GradeStructure} do NOT overlap.
 *
 * <p>Boundary convention: both ends inclusive ({@code [min, max]}). Two bands
 * overlap when {@code a.min &lt;= b.max AND b.min &lt;= a.max}. Touching
 * boundaries ({@code a.max == b.min}) are treated as overlap — adjacent bands
 * must be separated by at least one epsilon step (0.0001 with NUMERIC(12,4)).
 */
@Component
public class GradeBandOverlapValidator {

    /** Returns the list of overlap conflicts (empty when valid). */
    public List<Conflict> findOverlaps(List<GradeBand> bands) {
        List<Conflict> out = new ArrayList<>();
        if (bands == null || bands.size() < 2) {
            return out;
        }
        for (int i = 0; i < bands.size(); i++) {
            GradeBand a = bands.get(i);
            if (a.minScore() == null || a.maxScore() == null) continue;
            for (int j = i + 1; j < bands.size(); j++) {
                GradeBand b = bands.get(j);
                if (b.minScore() == null || b.maxScore() == null) continue;
                if (overlaps(a, b)) {
                    BigDecimal lo = a.minScore().max(b.minScore());
                    BigDecimal hi = a.maxScore().min(b.maxScore());
                    out.add(new Conflict(a.gradeId(), b.gradeId(), lo, hi));
                }
            }
        }
        return out;
    }

    /** True when the two bands intersect (inclusive on both ends). */
    public boolean overlaps(GradeBand a, GradeBand b) {
        return a.minScore().compareTo(b.maxScore()) <= 0
                && b.minScore().compareTo(a.maxScore()) <= 0;
    }

    /** Conflict tuple: the two grades that overlap + the intersection range. */
    public record Conflict(UUID gradeIdA, UUID gradeIdB, BigDecimal intersectionMin,
                            BigDecimal intersectionMax) { }
}
