package uz.hrlab.grading.methodology.application;

import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.ScoringMode;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Command for {@code CreateMethodologyFromScratchUseCase}. */
public record CreateMethodologyCommand(
        UUID projectId,
        String code,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n,
        MethodologyType methodologyType,
        ScoringMode scoringMode,
        BigDecimal targetTotalPoints
) { }
