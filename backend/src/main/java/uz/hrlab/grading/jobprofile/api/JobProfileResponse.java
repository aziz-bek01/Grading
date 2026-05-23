package uz.hrlab.grading.jobprofile.api;

import uz.hrlab.grading.jobprofile.domain.JobProfile;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record JobProfileResponse(
        UUID id,
        UUID projectId,
        UUID positionId,
        Map<String, String> purposeI18n,
        Map<String, String> mainDutiesI18n,
        Map<String, String> responsibilityAreaI18n,
        Map<String, String> authorityI18n,
        Map<String, String> kpiExpectedResultsI18n,
        Map<String, String> educationRequirementsI18n,
        Map<String, String> experienceRequirementsI18n,
        Map<String, String> knowledgeSkillsI18n,
        Map<String, String> internalInteractionsI18n,
        Map<String, String> externalInteractionsI18n,
        Map<String, String> workingConditionsI18n,
        Map<String, String> documentsRegulationsI18n,
        LocalDate actualizationDate,
        JobProfileStatus status,
        int revisionNumber,
        UUID previousRevisionId,
        OffsetDateTime submittedAt,
        UUID submittedBy,
        OffsetDateTime approvedAt,
        UUID approvedBy,
        OffsetDateTime lockedAt
) {
    public static JobProfileResponse from(JobProfile p) {
        return new JobProfileResponse(
                p.id(), p.projectId(), p.positionId(),
                p.purposeI18n(), p.mainDutiesI18n(), p.responsibilityAreaI18n(),
                p.authorityI18n(), p.kpiExpectedResultsI18n(),
                p.educationRequirementsI18n(), p.experienceRequirementsI18n(),
                p.knowledgeSkillsI18n(), p.internalInteractionsI18n(),
                p.externalInteractionsI18n(), p.workingConditionsI18n(),
                p.documentsRegulationsI18n(), p.actualizationDate(),
                p.status(), p.revisionNumber(), p.previousRevisionId(),
                p.submittedAt(), p.submittedBy(), p.approvedAt(),
                p.approvedBy(), p.lockedAt());
    }
}
