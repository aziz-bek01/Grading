package uz.hrlab.grading.gradestructure.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Detects gaps between adjacent {@link GradeBand}s (assumes bands are
 * non-overlapping — call {@link GradeBandOverlapValidator} first).
 *
 * <p>Epsilon for NUMERIC(12,4) is {@code 0.0001}: two bands are contiguous when
 * {@code next.min == prev.max + 0.0001}. Anything larger is a gap. Gaps
 * smaller than the epsilon are treated as 0 (contiguous) — matches the
 * methodology weight tolerance convention.
 */
@Component
public class GradeBandGapDetector {

    public static final BigDecimal EPSILON =
            new BigDecimal("0.0001").setScale(4, RoundingMode.HALF_UP);

    public List<Gap> findGaps(List<GradeBand> bands) {
        List<Gap> gaps = new ArrayList<>();
        if (bands == null || bands.size() < 2) {
            return gaps;
        }
        List<GradeBand> sorted = new ArrayList<>(bands);
        sorted.sort(Comparator.comparing(GradeBand::minScore));
        for (int i = 1; i < sorted.size(); i++) {
            GradeBand prev = sorted.get(i - 1);
            GradeBand curr = sorted.get(i);
            BigDecimal expected = prev.maxScore().add(EPSILON).setScale(4, RoundingMode.HALF_UP);
            if (curr.minScore().compareTo(expected) > 0) {
                gaps.add(new Gap(prev.gradeId(), curr.gradeId(), expected,
                        curr.minScore().subtract(EPSILON).setScale(4, RoundingMode.HALF_UP)));
            }
        }
        return gaps;
    }

    public record Gap(java.util.UUID afterGradeId, java.util.UUID beforeGradeId,
                       BigDecimal gapMin, BigDecimal gapMax) { }
}
