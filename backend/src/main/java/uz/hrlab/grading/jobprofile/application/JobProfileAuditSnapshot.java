package uz.hrlab.grading.jobprofile.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.audit.application.AuditJsonRedactor;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileJpaEntity;

/**
 * Builds before/after JSON snapshots for {@link JobProfileJpaEntity} using the
 * shared {@link AuditJsonRedactor} field allowlist + long-text preview policy
 * (security-blueprint §9.3, F-309).
 */
@Component
public class JobProfileAuditSnapshot {

    private final AuditJsonRedactor redactor;

    public JobProfileAuditSnapshot(AuditJsonRedactor redactor) {
        this.redactor = redactor;
    }

    public JsonNode of(JobProfileJpaEntity profile) {
        if (profile == null) return null;
        return redactor.builder()
                .put("id", profile.getId())
                .put("tenantId", profile.getTenantId())
                .put("projectId", profile.getProjectId())
                .put("positionId", profile.getPositionId())
                .put("status", profile.getStatus() == null ? null : profile.getStatus().name())
                .putRaw("revisionNumber", profile.getRevisionNumber())
                .put("previousRevisionId", profile.getPreviousRevisionId())
                .put("actualizationDate", profile.getActualizationDate() == null ? null
                        : profile.getActualizationDate().toString())
                .put("submittedAt", profile.getSubmittedAt())
                .put("submittedBy", profile.getSubmittedBy())
                .put("approvedAt", profile.getApprovedAt())
                .put("approvedBy", profile.getApprovedBy())
                .put("lockedAt", profile.getLockedAt())
                .addI18nPreviews("purposeI18n", profile.getPurposeI18n())
                .addI18nPreviews("mainDutiesI18n", profile.getMainDutiesI18n())
                .addI18nPreviews("responsibilityAreaI18n", profile.getResponsibilityAreaI18n())
                .addI18nPreviews("authorityI18n", profile.getAuthorityI18n())
                .addI18nPreviews("kpiExpectedResultsI18n", profile.getKpiExpectedResultsI18n())
                .addI18nPreviews("educationRequirementsI18n", profile.getEducationRequirementsI18n())
                .addI18nPreviews("experienceRequirementsI18n", profile.getExperienceRequirementsI18n())
                .addI18nPreviews("knowledgeSkillsI18n", profile.getKnowledgeSkillsI18n())
                .addI18nPreviews("internalInteractionsI18n", profile.getInternalInteractionsI18n())
                .addI18nPreviews("externalInteractionsI18n", profile.getExternalInteractionsI18n())
                .addI18nPreviews("workingConditionsI18n", profile.getWorkingConditionsI18n())
                .addI18nPreviews("documentsRegulationsI18n", profile.getDocumentsRegulationsI18n())
                .build();
    }
}
