package uz.hrlab.grading.gradestructure.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * GradeBand — score range [minScore, maxScore] (inclusive on both ends) for
 * one {@link Grade}.
 *
 * <p><b>Boundary convention:</b> both endpoints are inclusive. To preserve the
 * "no overlap" guarantee, adjacent bands must differ by the smallest
 * representable epsilon of NUMERIC(12,4) (0.0001). Application validation
 * enforces this; the {@link GradeBandOverlapValidator} treats touching bands
 * (max1 == min2) as overlapping.
 */
public final class GradeBand {

    private final UUID id;
    private final UUID tenantId;
    private final UUID gradeId;
    private final BigDecimal minScore;
    private final BigDecimal maxScore;

    public GradeBand(UUID id, UUID tenantId, UUID gradeId,
                     BigDecimal minScore, BigDecimal maxScore) {
        this.id = id;
        this.tenantId = tenantId;
        this.gradeId = gradeId;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID gradeId() { return gradeId; }
    public BigDecimal minScore() { return minScore; }
    public BigDecimal maxScore() { return maxScore; }
}
