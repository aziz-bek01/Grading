package uz.hrlab.grading.methodology.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.audit.application.AuditJsonRedactor;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyTemplateJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyTemplateSnapshot;
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

    /**
     * Epic E — tenant CUSTOM template snapshot. The frozen
     * {@code factors_snapshot} is NEVER reproduced verbatim (it can be large and
     * carries multilingual long text); only its factor count is recorded as
     * metadata. i18n name/description use the shared long-text preview policy.
     */
    public JsonNode of(MethodologyTemplateJpaEntity t) {
        if (t == null) return null;
        MethodologyTemplateSnapshot snap = t.getFactorsSnapshot();
        int factorCount = snap == null || snap.factors() == null ? 0 : snap.factors().size();
        return redactor.builder()
                .put("id", t.getId())
                .put("tenantId", t.getTenantId())
                .put("code", t.getCode())
                .put("methodologyType", t.getMethodologyType() == null ? null
                        : t.getMethodologyType().name())
                .put("scoringMode", t.getScoringMode() == null ? null
                        : t.getScoringMode().name())
                .put("targetTotalPoints", t.getTargetTotalPoints() == null ? null
                        : t.getTargetTotalPoints().toPlainString())
                .put("status", t.getStatus() == null ? null : t.getStatus().name())
                .putRaw("factorCount", factorCount)
                .addI18nPreviews("nameI18n", t.getNameI18n())
                .addI18nPreviews("descriptionI18n", t.getDescriptionI18n())
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
