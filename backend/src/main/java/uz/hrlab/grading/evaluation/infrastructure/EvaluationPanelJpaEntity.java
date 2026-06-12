package uz.hrlab.grading.evaluation.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Evaluation panel (multi-evaluator case). Changelog 034. */
@Entity
@Table(name = "evaluation_panels")
public class EvaluationPanelJpaEntity extends AuditedJpaEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private EvaluationPanelStatus status;

    @Column(name = "min_evaluators", nullable = false)
    private int minEvaluators = 3;

    // Server-computed only — null until AVERAGED.
    @Column(name = "raw_total_score", precision = 12, scale = 4)
    private BigDecimal rawTotalScore;

    @Column(name = "displayed_total_score", precision = 12, scale = 2)
    private BigDecimal displayedTotalScore;

    // Assigned from the averaged score on CEO approval (Phase-6 reuse).
    @Column(name = "grade_band_id")
    private UUID gradeBandId;

    @Column(name = "assigned_grade_number")
    private Integer assignedGradeNumber;

    @Column(name = "averaged_at")
    private OffsetDateTime averagedAt;
    @Column(name = "averaged_by")
    private UUID averagedBy;
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

    protected EvaluationPanelJpaEntity() { }

    public EvaluationPanelJpaEntity(UUID id, UUID tenantId, UUID projectId, UUID positionId,
                                    UUID methodologyVersionId, EvaluationPanelStatus status,
                                    int minEvaluators) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.positionId = positionId;
        this.methodologyVersionId = methodologyVersionId;
        this.status = status;
        this.minEvaluators = minEvaluators;
    }

    public EvaluationPanel toDomain() {
        return new EvaluationPanel(id, tenantId, projectId, positionId, methodologyVersionId,
                status, minEvaluators, rawTotalScore, displayedTotalScore,
                gradeBandId, assignedGradeNumber,
                averagedAt, averagedBy, submittedAt, submittedBy,
                approvedAt, approvedBy, lockedAt, lockedBy, archivedAt, archivedBy,
                getCreatedAt());
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getPositionId() { return positionId; }
    public UUID getMethodologyVersionId() { return methodologyVersionId; }
    public EvaluationPanelStatus getStatus() { return status; }
    public int getMinEvaluators() { return minEvaluators; }
    public BigDecimal getRawTotalScore() { return rawTotalScore; }
    public BigDecimal getDisplayedTotalScore() { return displayedTotalScore; }
    public UUID getGradeBandId() { return gradeBandId; }
    public Integer getAssignedGradeNumber() { return assignedGradeNumber; }
    public OffsetDateTime getAveragedAt() { return averagedAt; }
    public UUID getAveragedBy() { return averagedBy; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public UUID getSubmittedBy() { return submittedBy; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public UUID getApprovedBy() { return approvedBy; }
    public OffsetDateTime getLockedAt() { return lockedAt; }
    public UUID getLockedBy() { return lockedBy; }
    public OffsetDateTime getArchivedAt() { return archivedAt; }
    public UUID getArchivedBy() { return archivedBy; }

    public void setStatus(EvaluationPanelStatus v) { this.status = v; }
    public void setMinEvaluators(int v) { this.minEvaluators = v; }
    public void setRawTotalScore(BigDecimal v) { this.rawTotalScore = v; }
    public void setDisplayedTotalScore(BigDecimal v) { this.displayedTotalScore = v; }
    public void setGradeBandId(UUID v) { this.gradeBandId = v; }
    public void setAssignedGradeNumber(Integer v) { this.assignedGradeNumber = v; }
    public void setAveragedAt(OffsetDateTime v) { this.averagedAt = v; }
    public void setAveragedBy(UUID v) { this.averagedBy = v; }
    public void setSubmittedAt(OffsetDateTime v) { this.submittedAt = v; }
    public void setSubmittedBy(UUID v) { this.submittedBy = v; }
    public void setApprovedAt(OffsetDateTime v) { this.approvedAt = v; }
    public void setApprovedBy(UUID v) { this.approvedBy = v; }
    public void setLockedAt(OffsetDateTime v) { this.lockedAt = v; }
    public void setLockedBy(UUID v) { this.lockedBy = v; }
    public void setArchivedAt(OffsetDateTime v) { this.archivedAt = v; }
    public void setArchivedBy(UUID v) { this.archivedBy = v; }
}
