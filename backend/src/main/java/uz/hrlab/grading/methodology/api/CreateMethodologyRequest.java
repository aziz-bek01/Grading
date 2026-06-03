package uz.hrlab.grading.methodology.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import uz.hrlab.grading.common.validation.SupportedLocaleKeys;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.ScoringMode;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record CreateMethodologyRequest(
        UUID projectId,
        @NotBlank String code,
        @NotEmpty @SupportedLocaleKeys Map<String, String> nameI18n,
        @SupportedLocaleKeys Map<String, String> descriptionI18n,
        @NotNull MethodologyType methodologyType,
        @NotNull ScoringMode scoringMode,
        BigDecimal targetTotalPoints
) { }
