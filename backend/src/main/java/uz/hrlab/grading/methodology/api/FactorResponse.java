package uz.hrlab.grading.methodology.api;

import uz.hrlab.grading.methodology.domain.Factor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record FactorResponse(
        UUID id,
        UUID methodologyVersionId,
        String code,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n,
        BigDecimal weight,
        BigDecimal maxPoints,
        int sortOrder,
        boolean required
) {
    public static FactorResponse from(Factor f) {
        return new FactorResponse(f.id(), f.methodologyVersionId(), f.code(),
                f.nameI18n(), f.descriptionI18n(),
                f.weight(), f.maxPoints(), f.sortOrder(), f.required());
    }
}
