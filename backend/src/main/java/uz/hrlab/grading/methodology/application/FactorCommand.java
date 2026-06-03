package uz.hrlab.grading.methodology.application;

import java.math.BigDecimal;
import java.util.Map;

/** Body for AddFactor / UpdateFactor. */
public record FactorCommand(
        String code,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n,
        BigDecimal weight,
        BigDecimal maxPoints,
        Integer sortOrder,
        Boolean required
) { }
