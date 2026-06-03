package uz.hrlab.grading.methodology.api;

import uz.hrlab.grading.common.validation.SupportedLocaleKeys;

import java.math.BigDecimal;
import java.util.Map;

public record FactorRequest(
        String code,
        @SupportedLocaleKeys Map<String, String> nameI18n,
        @SupportedLocaleKeys Map<String, String> descriptionI18n,
        BigDecimal weight,
        BigDecimal maxPoints,
        Integer sortOrder,
        Boolean required
) { }
