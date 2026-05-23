package uz.hrlab.grading.jobprofile.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.jobprofile.domain.JobProfile;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "job_profiles")
public class JobProfileJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "position_id", nullable = false, updatable = false)
    private UUID positionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "purpose_i18n", columnDefinition = "jsonb")
    private Map<String, String> purposeI18n = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "main_duties_i18n", columnDefinition = "jsonb")
    private Map<String, String> mainDutiesI18n = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "responsibility_area_i18n", columnDefinition = "jsonb")
    private Map<String, String> responsibilityAreaI18n = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "authority_i18n", columnDefinition = "jsonb")
    private Map<String, String> authorityI18n = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "kpi_expected_results_i18n", columnDefinition = "jsonb")
    private Map<String, String> kpiExpectedResultsI18n = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "education_requirements_i18n", columnDefinition = "jsonb")
    private Map<String, String> educationRequirementsI18n = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "experience_requirements_i18n", columnDefinition = "jsonb")
    private Map<String, String> experienceRequirementsI18n = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "knowledge_skills_i18n", columnDefinition = "jsonb")
    private Map<String, String> knowledgeSkillsI18n = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "internal_interactions_i18n", columnDefinition = "jsonb")
    private Map<String, String> internalInteractionsI18n = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "external_interactions_i18n", columnDefinition = "jsonb")
    private Map<String, String> externalInteractionsI18n = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "working_conditions_i18n", columnDefinition = "jsonb")
    private Map<String, String> workingConditionsI18n = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "documents_regulations_i18n", columnDefinition = "jsonb")
    private Map<String, String> documentsRegulationsI18n = new HashMap<>();

    @Column(name = "actualization_date")
    private LocalDate actualizationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private JobProfileStatus status;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "previous_revision_id")
    private UUID previousRevisionId;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    protected JobProfileJpaEntity() { }

    public JobProfileJpaEntity(UUID id, UUID tenantId, UUID projectId, UUID positionId,
                               JobProfileStatus status, int revisionNumber,
                               UUID previousRevisionId) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.positionId = positionId;
        this.status = status;
        this.revisionNumber = revisionNumber;
        this.previousRevisionId = previousRevisionId;
    }

    public JobProfile toDomain() {
        return new JobProfile(id, tenantId, projectId, positionId,
                purposeI18n, mainDutiesI18n, responsibilityAreaI18n, authorityI18n,
                kpiExpectedResultsI18n, educationRequirementsI18n,
                experienceRequirementsI18n, knowledgeSkillsI18n,
                internalInteractionsI18n, externalInteractionsI18n,
                workingConditionsI18n, documentsRegulationsI18n,
                actualizationDate, status, revisionNumber, previousRevisionId,
                submittedAt, submittedBy, approvedAt, approvedBy, lockedAt);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getPositionId() { return positionId; }
    public Map<String, String> getPurposeI18n() { return purposeI18n; }
    public Map<String, String> getMainDutiesI18n() { return mainDutiesI18n; }
    public Map<String, String> getResponsibilityAreaI18n() { return responsibilityAreaI18n; }
    public Map<String, String> getAuthorityI18n() { return authorityI18n; }
    public Map<String, String> getKpiExpectedResultsI18n() { return kpiExpectedResultsI18n; }
    public Map<String, String> getEducationRequirementsI18n() { return educationRequirementsI18n; }
    public Map<String, String> getExperienceRequirementsI18n() { return experienceRequirementsI18n; }
    public Map<String, String> getKnowledgeSkillsI18n() { return knowledgeSkillsI18n; }
    public Map<String, String> getInternalInteractionsI18n() { return internalInteractionsI18n; }
    public Map<String, String> getExternalInteractionsI18n() { return externalInteractionsI18n; }
    public Map<String, String> getWorkingConditionsI18n() { return workingConditionsI18n; }
    public Map<String, String> getDocumentsRegulationsI18n() { return documentsRegulationsI18n; }
    public LocalDate getActualizationDate() { return actualizationDate; }
    public JobProfileStatus getStatus() { return status; }
    public int getRevisionNumber() { return revisionNumber; }
    public UUID getPreviousRevisionId() { return previousRevisionId; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public UUID getSubmittedBy() { return submittedBy; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public UUID getApprovedBy() { return approvedBy; }
    public OffsetDateTime getLockedAt() { return lockedAt; }

    public void setPurposeI18n(Map<String, String> v) { this.purposeI18n = nullSafe(v); }
    public void setMainDutiesI18n(Map<String, String> v) { this.mainDutiesI18n = nullSafe(v); }
    public void setResponsibilityAreaI18n(Map<String, String> v) { this.responsibilityAreaI18n = nullSafe(v); }
    public void setAuthorityI18n(Map<String, String> v) { this.authorityI18n = nullSafe(v); }
    public void setKpiExpectedResultsI18n(Map<String, String> v) { this.kpiExpectedResultsI18n = nullSafe(v); }
    public void setEducationRequirementsI18n(Map<String, String> v) { this.educationRequirementsI18n = nullSafe(v); }
    public void setExperienceRequirementsI18n(Map<String, String> v) { this.experienceRequirementsI18n = nullSafe(v); }
    public void setKnowledgeSkillsI18n(Map<String, String> v) { this.knowledgeSkillsI18n = nullSafe(v); }
    public void setInternalInteractionsI18n(Map<String, String> v) { this.internalInteractionsI18n = nullSafe(v); }
    public void setExternalInteractionsI18n(Map<String, String> v) { this.externalInteractionsI18n = nullSafe(v); }
    public void setWorkingConditionsI18n(Map<String, String> v) { this.workingConditionsI18n = nullSafe(v); }
    public void setDocumentsRegulationsI18n(Map<String, String> v) { this.documentsRegulationsI18n = nullSafe(v); }
    public void setActualizationDate(LocalDate v) { this.actualizationDate = v; }
    public void setStatus(JobProfileStatus v) { this.status = v; }
    public void setSubmittedAt(OffsetDateTime v) { this.submittedAt = v; }
    public void setSubmittedBy(UUID v) { this.submittedBy = v; }
    public void setApprovedAt(OffsetDateTime v) { this.approvedAt = v; }
    public void setApprovedBy(UUID v) { this.approvedBy = v; }
    public void setLockedAt(OffsetDateTime v) { this.lockedAt = v; }

    private static Map<String, String> nullSafe(Map<String, String> in) {
        return in == null ? new HashMap<>() : new HashMap<>(in);
    }
}
