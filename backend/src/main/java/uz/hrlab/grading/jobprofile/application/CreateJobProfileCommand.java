package uz.hrlab.grading.jobprofile.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record CreateJobProfileCommand(
        @NotNull UUID positionId,
        Map<@NotBlank String, @Size(max = 20000) String> purposeI18n,
        Map<@NotBlank String, @Size(max = 20000) String> mainDutiesI18n,
        Map<@NotBlank String, @Size(max = 20000) String> responsibilityAreaI18n,
        Map<@NotBlank String, @Size(max = 20000) String> authorityI18n,
        Map<@NotBlank String, @Size(max = 20000) String> kpiExpectedResultsI18n,
        Map<@NotBlank String, @Size(max = 20000) String> educationRequirementsI18n,
        Map<@NotBlank String, @Size(max = 20000) String> experienceRequirementsI18n,
        Map<@NotBlank String, @Size(max = 20000) String> knowledgeSkillsI18n,
        Map<@NotBlank String, @Size(max = 20000) String> internalInteractionsI18n,
        Map<@NotBlank String, @Size(max = 20000) String> externalInteractionsI18n,
        Map<@NotBlank String, @Size(max = 20000) String> workingConditionsI18n,
        Map<@NotBlank String, @Size(max = 20000) String> documentsRegulationsI18n,
        LocalDate actualizationDate
) { }
