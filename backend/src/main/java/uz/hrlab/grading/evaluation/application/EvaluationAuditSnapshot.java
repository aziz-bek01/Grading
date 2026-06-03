package uz.hrlab.grading.evaluation.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.audit.application.AuditJsonRedactor;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationCalibrationEventJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreJpaEntity;

/**
 * Phase 5 audit snapshot helper — metadata only, no comment / reason bodies
 * (security-blueprint §9.3, AuditJsonRedactor preview policy).
 *
 * <p>Reasons are passed via the audit row's {@code reason} field where they
 * are length-capped and redacted by the redactor — never via beforeJson /
 * afterJson.
 */
@Component
public class EvaluationAuditSnapshot {

    private final AuditJsonRedactor redactor;

    public EvaluationAuditSnapshot(AuditJsonRedactor redactor) {
        this.redactor = redactor;
    }

    public JsonNode of(EvaluationJpaEntity e) {
        if (e == null) return null;
        return redactor.builder()
                .put("id", e.getId())
                .put("tenantId", e.getTenantId())
                .put("projectId", e.getProjectId())
                .put("positionId", e.getPositionId())
                .put("methodologyVersionId", e.getMethodologyVersionId())
                .put("evaluatorUserId", e.getEvaluatorUserId())
                .put("status", e.getStatus() == null ? null : e.getStatus().name())
                .put("rawTotalScore", e.getRawTotalScore() == null ? null
                        : e.getRawTotalScore().toPlainString())
                .put("displayedTotalScore", e.getDisplayedTotalScore() == null ? null
                        : e.getDisplayedTotalScore().toPlainString())
                .put("gradeBandId", e.getGradeBandId())
                .putRaw("assignedGradeNumber", e.getAssignedGradeNumber())
                .put("submittedAt", e.getSubmittedAt())
                .put("submittedBy", e.getSubmittedBy())
                .put("approvedAt", e.getApprovedAt())
                .put("approvedBy", e.getApprovedBy())
                .put("lockedAt", e.getLockedAt())
                .put("lockedBy", e.getLockedBy())
                .put("archivedAt", e.getArchivedAt())
                .put("archivedBy", e.getArchivedBy())
                .build();
    }

    public JsonNode of(EvaluationScoreJpaEntity s) {
        if (s == null) return null;
        return redactor.builder()
                .put("id", s.getId())
                .put("tenantId", s.getTenantId())
                .put("evaluationId", s.getEvaluationId())
                .put("factorId", s.getFactorId())
                .put("factorLevelId", s.getFactorLevelId())
                .put("rawFactorScore", s.getRawFactorScore() == null ? null
                        : s.getRawFactorScore().toPlainString())
                .putRaw("isManuallyAdjusted", s.isManuallyAdjusted())
                .put("originalFactorScore", s.getOriginalFactorScore() == null ? null
                        : s.getOriginalFactorScore().toPlainString())
                .put("adjustedByUserId", s.getAdjustedByUserId())
                .put("adjustedAt", s.getAdjustedAt())
                .build();
    }

    public JsonNode of(EvaluationCalibrationEventJpaEntity c) {
        if (c == null) return null;
        return redactor.builder()
                .put("id", c.getId())
                .put("tenantId", c.getTenantId())
                .put("evaluationId", c.getEvaluationId())
                .put("factorId", c.getFactorId())
                .put("originalScore", c.getOriginalScore() == null ? null
                        : c.getOriginalScore().toPlainString())
                .put("adjustedScore", c.getAdjustedScore() == null ? null
                        : c.getAdjustedScore().toPlainString())
                .put("delta", c.getDelta() == null ? null : c.getDelta().toPlainString())
                .put("decidedBy", c.getDecidedBy())
                .put("decidedAt", c.getDecidedAt())
                .build();
    }
}
