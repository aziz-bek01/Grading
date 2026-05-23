package uz.hrlab.grading.jobprofile.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JobProfile domain model (PRD §E6, database-blueprint job_profiles row).
 *
 * <p>Multilingual long-text fields are {@code Map<locale,String>} where the
 * locale key is one of {@code ru-RU}, {@code uz-Cyrl-UZ}, {@code uz-Latn-UZ},
 * {@code en-US}. Primary locale is mandatory before submit-for-review.
 *
 * <p>Approved profiles are immutable — see
 * {@link JobProfileImmutabilityPolicy} and the use cases.
 */
public final class JobProfile {

    private final UUID id;
    private final UUID tenantId;
    private final UUID projectId;
    private final UUID positionId;
    private final Map<String, String> purposeI18n;
    private final Map<String, String> mainDutiesI18n;
    private final Map<String, String> responsibilityAreaI18n;
    private final Map<String, String> authorityI18n;
    private final Map<String, String> kpiExpectedResultsI18n;
    private final Map<String, String> educationRequirementsI18n;
    private final Map<String, String> experienceRequirementsI18n;
    private final Map<String, String> knowledgeSkillsI18n;
    private final Map<String, String> internalInteractionsI18n;
    private final Map<String, String> externalInteractionsI18n;
    private final Map<String, String> workingConditionsI18n;
    private final Map<String, String> documentsRegulationsI18n;
    private final LocalDate actualizationDate;
    private final JobProfileStatus status;
    private final int revisionNumber;
    private final UUID previousRevisionId;
    private final OffsetDateTime submittedAt;
    private final UUID submittedBy;
    private final OffsetDateTime approvedAt;
    private final UUID approvedBy;
    private final OffsetDateTime lockedAt;

    public JobProfile(UUID id, UUID tenantId, UUID projectId, UUID positionId,
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
                      OffsetDateTime submittedAt, UUID submittedBy,
                      OffsetDateTime approvedAt, UUID approvedBy,
                      OffsetDateTime lockedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.positionId = positionId;
        this.purposeI18n = copy(purposeI18n);
        this.mainDutiesI18n = copy(mainDutiesI18n);
        this.responsibilityAreaI18n = copy(responsibilityAreaI18n);
        this.authorityI18n = copy(authorityI18n);
        this.kpiExpectedResultsI18n = copy(kpiExpectedResultsI18n);
        this.educationRequirementsI18n = copy(educationRequirementsI18n);
        this.experienceRequirementsI18n = copy(experienceRequirementsI18n);
        this.knowledgeSkillsI18n = copy(knowledgeSkillsI18n);
        this.internalInteractionsI18n = copy(internalInteractionsI18n);
        this.externalInteractionsI18n = copy(externalInteractionsI18n);
        this.workingConditionsI18n = copy(workingConditionsI18n);
        this.documentsRegulationsI18n = copy(documentsRegulationsI18n);
        this.actualizationDate = actualizationDate;
        this.status = status;
        this.revisionNumber = revisionNumber;
        this.previousRevisionId = previousRevisionId;
        this.submittedAt = submittedAt;
        this.submittedBy = submittedBy;
        this.approvedAt = approvedAt;
        this.approvedBy = approvedBy;
        this.lockedAt = lockedAt;
    }

    private static Map<String, String> copy(Map<String, String> src) {
        return src == null ? new HashMap<>() : new HashMap<>(src);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID projectId() { return projectId; }
    public UUID positionId() { return positionId; }
    public Map<String, String> purposeI18n() { return purposeI18n; }
    public Map<String, String> mainDutiesI18n() { return mainDutiesI18n; }
    public Map<String, String> responsibilityAreaI18n() { return responsibilityAreaI18n; }
    public Map<String, String> authorityI18n() { return authorityI18n; }
    public Map<String, String> kpiExpectedResultsI18n() { return kpiExpectedResultsI18n; }
    public Map<String, String> educationRequirementsI18n() { return educationRequirementsI18n; }
    public Map<String, String> experienceRequirementsI18n() { return experienceRequirementsI18n; }
    public Map<String, String> knowledgeSkillsI18n() { return knowledgeSkillsI18n; }
    public Map<String, String> internalInteractionsI18n() { return internalInteractionsI18n; }
    public Map<String, String> externalInteractionsI18n() { return externalInteractionsI18n; }
    public Map<String, String> workingConditionsI18n() { return workingConditionsI18n; }
    public Map<String, String> documentsRegulationsI18n() { return documentsRegulationsI18n; }
    public LocalDate actualizationDate() { return actualizationDate; }
    public JobProfileStatus status() { return status; }
    public int revisionNumber() { return revisionNumber; }
    public UUID previousRevisionId() { return previousRevisionId; }
    public OffsetDateTime submittedAt() { return submittedAt; }
    public UUID submittedBy() { return submittedBy; }
    public OffsetDateTime approvedAt() { return approvedAt; }
    public UUID approvedBy() { return approvedBy; }
    public OffsetDateTime lockedAt() { return lockedAt; }
}
