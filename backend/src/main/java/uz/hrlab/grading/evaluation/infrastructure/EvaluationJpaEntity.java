package uz.hrlab.grading.evaluation.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.evaluation.domain.Evaluation;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "evaluations")
public class EvaluationJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "position_id", nullable = false, updatable = false)
    private UUID positionId;

    @Column(name = "methodology_version_id", nullable = false, updatable = false)
    private UUID methodologyVersionId;

    @Column(name = "evaluator_user_id", nullable = false)
    private UUID evaluatorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private EvaluationStatus status;

    // MVP2 multi-evaluator (changelog 035) — nullable: legacy single evaluations
    // are backfilled into a min_evaluators=1 panel; brand-new single evaluations
    // created outside a panel keep these null until linked.
    @Column(name = "panel_id")
    private UUID panelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluator_role", length = 40)
    private uz.hrlab.grading.evaluation.domain.EvaluatorRole evaluatorRole;

    @Column(name = "raw_total_score", precision = 12, scale = 4, nullable = false)
    private BigDecimal rawTotalScore = BigDecimal.ZERO;

    @Column(name = "displayed_total_score", precision = 12, scale = 2, nullable = false)
    private BigDecimal displayedTotalScore = BigDecimal.ZERO;

    @Column(name = "grade_band_id")
    private UUID gradeBandId;

    @Column(name = "assigned_grade_number")
    private Integer assignedGradeNumber;

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

    @Column(name = "locked_by")
    private UUID lockedBy;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    protected EvaluationJpaEntity() { }

    public EvaluationJpaEntity(UUID id, UUID tenantId, UUID projectId, UUID positionId,
                               UUID methodologyVersionId, UUID evaluatorUserId,
                               EvaluationStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.positionId = positionId;
        this.methodologyVersionId = methodologyVersionId;
        this.evaluatorUserId = evaluatorUserId;
        this.status = status;
    }

    public Evaluation toDomain() {
        return new Evaluation(id, tenantId, projectId, positionId, methodologyVersionId,
                evaluatorUserId, status, rawTotalScore, displayedTotalScore,
                gradeBandId, assignedGradeNumber,
                submittedAt, submittedBy, approvedAt, approvedBy,
                lockedAt, lockedBy, archivedAt, archivedBy);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getPositionId() { return positionId; }
    public UUID getMethodologyVersionId() { return methodologyVersionId; }
    public UUID getEvaluatorUserId() { return evaluatorUserId; }
    public EvaluationStatus getStatus() { return status; }
    public BigDecimal getRawTotalScore() { return rawTotalScore; }
    public BigDecimal getDisplayedTotalScore() { return displayedTotalScore; }
    public UUID getGradeBandId() { return gradeBandId; }
    public Integer getAssignedGradeNumber() { return assignedGradeNumber; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public UUID getSubmittedBy() { return submittedBy; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public UUID getApprovedBy() { return approvedBy; }
    public OffsetDateTime getLockedAt() { return lockedAt; }
    public UUID getLockedBy() { return lockedBy; }
    public OffsetDateTime getArchivedAt() { return archivedAt; }
    public UUID getArchivedBy() { return archivedBy; }
    public UUID getPanelId() { return panelId; }
    public uz.hrlab.grading.evaluation.domain.EvaluatorRole getEvaluatorRole() { return evaluatorRole; }

    public void setEvaluatorUserId(UUID v) { this.evaluatorUserId = v; }
    public void setPanelId(UUID v) { this.panelId = v; }
    public void setEvaluatorRole(uz.hrlab.grading.evaluation.domain.EvaluatorRole v) {
        this.evaluatorRole = v;
    }
    public void setStatus(EvaluationStatus v) { this.status = v; }
    public void setRawTotalScore(BigDecimal v) {
        this.rawTotalScore = v == null ? BigDecimal.ZERO : v;
    }
    public void setDisplayedTotalScore(BigDecimal v) {
        this.displayedTotalScore = v == null ? BigDecimal.ZERO : v;
    }
    public void setGradeBandId(UUID v) { this.gradeBandId = v; }
    public void setAssignedGradeNumber(Integer v) { this.assignedGradeNumber = v; }
    public void setSubmittedAt(OffsetDateTime v) { this.submittedAt = v; }
    public void setSubmittedBy(UUID v) { this.submittedBy = v; }
    public void setApprovedAt(OffsetDateTime v) { this.approvedAt = v; }
    public void setApprovedBy(UUID v) { this.approvedBy = v; }
    public void setLockedAt(OffsetDateTime v) { this.lockedAt = v; }
    public void setLockedBy(UUID v) { this.lockedBy = v; }
    public void setArchivedAt(OffsetDateTime v) { this.archivedAt = v; }
    public void setArchivedBy(UUID v) { this.archivedBy = v; }
}
