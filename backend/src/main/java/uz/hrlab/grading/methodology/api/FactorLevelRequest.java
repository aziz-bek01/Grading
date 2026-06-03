package uz.hrlab.grading.methodology.api;

import uz.hrlab.grading.common.validation.SupportedLocaleKeys;

import java.math.BigDecimal;
import java.util.Map;

public record FactorLevelRequest(
        String code,
        Integer levelOrder,
        BigDecimal points,
        BigDecimal scaleValue,
        @SupportedLocaleKeys Map<String, String> labelI18n,
        @SupportedLocaleKeys Map<String, String> descriptionI18n
) { }
