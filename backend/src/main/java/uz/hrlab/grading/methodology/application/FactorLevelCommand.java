package uz.hrlab.grading.methodology.application;

import java.math.BigDecimal;
import java.util.Map;

/** Body for AddFactorLevel / UpdateFactorLevel. */
public record FactorLevelCommand(
        String code,
        Integer levelOrder,
        BigDecimal points,
        BigDecimal scaleValue,
        Map<String, String> labelI18n,
        Map<String, String> descriptionI18n
) { }
