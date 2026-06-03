package uz.hrlab.grading.methodology.api;

import uz.hrlab.grading.methodology.domain.FactorLevel;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record FactorLevelResponse(
        UUID id,
        UUID factorId,
        String code,
        int levelOrder,
        BigDecimal points,
        BigDecimal scaleValue,
        Map<String, String> labelI18n,
        Map<String, String> descriptionI18n
) {
    public static FactorLevelResponse from(FactorLevel l) {
        return new FactorLevelResponse(l.id(), l.factorId(), l.code(),
                l.levelOrder(), l.points(), l.scaleValue(),
                l.labelI18n(), l.descriptionI18n());
    }
}
