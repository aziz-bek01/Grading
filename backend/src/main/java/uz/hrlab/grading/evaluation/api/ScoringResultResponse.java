package uz.hrlab.grading.evaluation.api;

import uz.hrlab.grading.evaluation.domain.ScoringResult;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record ScoringResultResponse(
        BigDecimal rawTotal,
        BigDecimal displayedTotal,
        Map<UUID, BigDecimal> perFactorRaw
) {
    public static ScoringResultResponse from(ScoringResult r) {
        return new ScoringResultResponse(
                r.rawTotal(),
                r.displayedTotal(),
                r.perFactorRaw() == null ? Map.of() : new HashMap<>(r.perFactorRaw()));
    }
}
