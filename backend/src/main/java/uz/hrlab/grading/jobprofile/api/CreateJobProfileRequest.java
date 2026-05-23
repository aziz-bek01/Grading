package uz.hrlab.grading.jobprofile.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import uz.hrlab.grading.common.validation.SupportedLocaleKeys;

import java.time.LocalDate;
import java.util.Map;

/**
 * POST /api/v1/positions/{positionId}/job-profile body. {@code positionId} is
 * sourced from the path — never from the body. {@code tenantId} is sourced
 * from the JWT/TenantContext — never from the body (ArchUnit rule 7).
 *
 * <p>Every i18n map is locale-key-bounded via {@link SupportedLocaleKeys}
 * (F-308 — reject unknown locale codes at the API edge).
 */
public record CreateJobProfileRequest(
        @SupportedLocaleKeys Map<@NotBlank String, @Size(max = 20000) String> purposeI18n,
        @SupportedLocaleKeys Map<@NotBlank String, @Size(max = 20000) String> mainDutiesI18n,
        @SupportedLocaleKeys Map<@NotBlank String, @Size(max = 20000) String> responsibilityAreaI18n,
        @SupportedLocaleKeys Map<@NotBlank String, @Size(max = 20000) String> authorityI18n,
        @SupportedLocaleKeys Map<@NotBlank String, @Size(max = 20000) String> kpiExpectedResultsI18n,
        @SupportedLocaleKeys Map<@NotBlank String, @Size(max = 20000) String> educationRequirementsI18n,
        @SupportedLocaleKeys Map<@NotBlank String, @Size(max = 20000) String> experienceRequirementsI18n,
        @SupportedLocaleKeys Map<@NotBlank String, @Size(max = 20000) String> knowledgeSkillsI18n,
        @SupportedLocaleKeys Map<@NotBlank String, @Size(max = 20000) String> internalInteractionsI18n,
        @SupportedLocaleKeys Map<@NotBlank String, @Size(max = 20000) String> externalInteractionsI18n,
        @SupportedLocaleKeys Map<@NotBlank String, @Size(max = 20000) String> workingConditionsI18n,
        @SupportedLocaleKeys Map<@NotBlank String, @Size(max = 20000) String> documentsRegulationsI18n,
        LocalDate actualizationDate
) { }
