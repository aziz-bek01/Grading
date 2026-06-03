package uz.hrlab.grading.gradestructure.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.audit.application.AuditJsonRedactor;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;

/** Audit snapshot helper — mirrors MethodologyAuditSnapshot. */
@Component
public class GradeStructureAuditSnapshot {

    private final AuditJsonRedactor redactor;

    public GradeStructureAuditSnapshot(AuditJsonRedactor redactor) {
        this.redactor = redactor;
    }

    public JsonNode of(GradeStructureJpaEntity s) {
        if (s == null) return null;
        return redactor.builder()
                .put("id", s.getId())
                .put("tenantId", s.getTenantId())
                .put("projectId", s.getProjectId())
                .put("code", s.getCode())
                .put("structureType", s.getStructureType() == null ? null
                        : s.getStructureType().name())
                .put("status", s.getStatus() == null ? null : s.getStatus().name())
                .putRaw("versionNumber", s.getVersionNumber())
                .put("previousVersionId", s.getPreviousVersionId())
                .put("gapPolicy", s.getGapPolicy() == null ? null : s.getGapPolicy().name())
                .addI18nPreviews("nameI18n", s.getNameI18n())
                .addI18nPreviews("descriptionI18n", s.getDescriptionI18n())
                .put("approvedAt", s.getApprovedAt())
                .put("approvedBy", s.getApprovedBy())
                .put("lockedAt", s.getLockedAt())
                .put("lockedBy", s.getLockedBy())
                .build();
    }

    public JsonNode of(GradeJpaEntity g) {
        if (g == null) return null;
        return redactor.builder()
                .put("id", g.getId())
                .put("tenantId", g.getTenantId())
                .put("gradeStructureId", g.getGradeStructureId())
                .putRaw("gradeNumber", g.getGradeNumber())
                .putRaw("sortOrder", g.getSortOrder())
                .addI18nPreviews("nameI18n", g.getNameI18n())
                .addI18nPreviews("descriptionI18n", g.getDescriptionI18n())
                .build();
    }

    public JsonNode of(GradeBandJpaEntity b) {
        if (b == null) return null;
        return redactor.builder()
                .put("id", b.getId())
                .put("tenantId", b.getTenantId())
                .put("gradeId", b.getGradeId())
                .put("gradeStructureId", b.getGradeStructureId())
                .put("minScore", b.getMinScore() == null ? null : b.getMinScore().toPlainString())
                .put("maxScore", b.getMaxScore() == null ? null : b.getMaxScore().toPlainString())
                .build();
    }
}
