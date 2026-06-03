package uz.hrlab.grading.methodology.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.audit.application.AuditJsonRedactor;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;

/**
 * Audit snapshot helper — uses the shared {@link AuditJsonRedactor} long-text
 * preview policy for multilingual maps (security-blueprint §9.3, F-309).
 */
@Component
public class MethodologyAuditSnapshot {

    private final AuditJsonRedactor redactor;

    public MethodologyAuditSnapshot(AuditJsonRedactor redactor) {
        this.redactor = redactor;
    }

    public JsonNode of(MethodologyJpaEntity m) {
        if (m == null) return null;
        return redactor.builder()
                .put("id", m.getId())
                .put("tenantId", m.getTenantId())
                .put("projectId", m.getProjectId())
                .put("code", m.getCode())
                .put("methodologyType", m.getMethodologyType() == null ? null
                        : m.getMethodologyType().name())
                .put("status", m.getStatus() == null ? null : m.getStatus().name())
                .addI18nPreviews("nameI18n", m.getNameI18n())
                .addI18nPreviews("descriptionI18n", m.getDescriptionI18n())
                .build();
    }

    public JsonNode of(MethodologyVersionJpaEntity v) {
        if (v == null) return null;
        return redactor.builder()
                .put("id", v.getId())
                .put("tenantId", v.getTenantId())
                .put("methodologyId", v.getMethodologyId())
                .putRaw("versionNumber", v.getVersionNumber())
                .put("status", v.getStatus() == null ? null : v.getStatus().name())
                .put("scoringMode", v.getScoringMode() == null ? null : v.getScoringMode().name())
                .put("targetTotalPoints", v.getTargetTotalPoints() == null ? null
                        : v.getTargetTotalPoints().toPlainString())
                .put("previousVersionId", v.getPreviousVersionId())
                .put("approvedAt", v.getApprovedAt())
                .put("approvedBy", v.getApprovedBy())
                .put("lockedAt", v.getLockedAt())
                .put("lockedBy", v.getLockedBy())
                .build();
    }

    public JsonNode of(FactorJpaEntity f) {
        if (f == null) return null;
        return redactor.builder()
                .put("id", f.getId())
                .put("tenantId", f.getTenantId())
                .put("methodologyVersionId", f.getMethodologyVersionId())
                .put("code", f.getCode())
                .put("weight", f.getWeight() == null ? null : f.getWeight().toPlainString())
                .put("maxPoints", f.getMaxPoints() == null ? null : f.getMaxPoints().toPlainString())
                .putRaw("sortOrder", f.getSortOrder())
                .putRaw("required", f.isRequired())
                .addI18nPreviews("nameI18n", f.getNameI18n())
                .addI18nPreviews("descriptionI18n", f.getDescriptionI18n())
                .build();
    }

    public JsonNode of(FactorLevelJpaEntity l) {
        if (l == null) return null;
        return redactor.builder()
                .put("id", l.getId())
                .put("tenantId", l.getTenantId())
                .put("factorId", l.getFactorId())
                .put("code", l.getCode())
                .putRaw("levelOrder", l.getLevelOrder())
                .put("points", l.getPoints() == null ? null : l.getPoints().toPlainString())
                .put("scaleValue", l.getScaleValue() == null ? null
                        : l.getScaleValue().toPlainString())
                .addI18nPreviews("labelI18n", l.getLabelI18n())
                .addI18nPreviews("descriptionI18n", l.getDescriptionI18n())
                .build();
    }
}
