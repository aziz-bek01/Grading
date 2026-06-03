package uz.hrlab.grading.evaluation.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evaluation domain model — root aggregate of the scoring module
 * (architecture §9 + §15; PRD Phase 5).
 *
 * <p>State machine on {@link EvaluationStatus}; immutability rules on
 * {@link EvaluationImmutabilityPolicy}. Score computation by
 * {@link EvaluationScoringEngine}.
 *
 * <p>{@code rawTotalScore} (NUMERIC(12,4)) is the official score used for
 * grade assignment (architecture §15.4); {@code displayedTotalScore}
 * (NUMERIC(12,2)) is for UI display only.
 *
 * <p>{@code gradeBandId} + {@code assignedGradeNumber} are Phase 6 fields;
 * Phase 5 leaves them null.
 */
public final class Evaluation {

    private final UUID id;
    private final UUID tenantId;
    private final UUID projectId;
    private final UUID positionId;
    private final UUID methodologyVersionId;
    private final UUID evaluatorUserId;
    private final EvaluationStatus status;
    private final BigDecimal rawTotalScore;
    private final BigDecimal displayedTotalScore;
    private final UUID gradeBandId;
    private final Integer assignedGradeNumber;
    private final OffsetDateTime submittedAt;
    private final UUID submittedBy;
    private final OffsetDateTime approvedAt;
    private final UUID approvedBy;
    private final OffsetDateTime lockedAt;
    private final UUID lockedBy;
    private final OffsetDateTime archivedAt;
    private final UUID archivedBy;

    public Evaluation(UUID id, UUID tenantId, UUID projectId, UUID positionId,
                      UUID methodologyVersionId, UUID evaluatorUserId,
                      EvaluationStatus status,
                      BigDecimal rawTotalScore, BigDecimal displayedTotalScore,
                      UUID gradeBandId, Integer assignedGradeNumber,
                      OffsetDateTime submittedAt, UUID submittedBy,
                      OffsetDateTime approvedAt, UUID approvedBy,
                      OffsetDateTime lockedAt, UUID lockedBy,
                      OffsetDateTime archivedAt, UUID archivedBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.positionId = positionId;
        this.methodologyVersionId = methodologyVersionId;
        this.evaluatorUserId = evaluatorUserId;
        this.status = status;
        this.rawTotalScore = rawTotalScore;
        this.displayedTotalScore = displayedTotalScore;
        this.gradeBandId = gradeBandId;
        this.assignedGradeNumber = assignedGradeNumber;
        this.submittedAt = submittedAt;
        this.submittedBy = submittedBy;
        this.approvedAt = approvedAt;
        this.approvedBy = approvedBy;
        this.lockedAt = lockedAt;
        this.lockedBy = lockedBy;
        this.archivedAt = archivedAt;
        this.archivedBy = archivedBy;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID projectId() { return projectId; }
    public UUID positionId() { return positionId; }
    public UUID methodologyVersionId() { return methodologyVersionId; }
    public UUID evaluatorUserId() { return evaluatorUserId; }
    public EvaluationStatus status() { return status; }
    public BigDecimal rawTotalScore() { return rawTotalScore; }
    public BigDecimal displayedTotalScore() { return displayedTotalScore; }
    public UUID gradeBandId() { return gradeBandId; }
    public Integer assignedGradeNumber() { return assignedGradeNumber; }
    public OffsetDateTime submittedAt() { return submittedAt; }
    public UUID submittedBy() { return submittedBy; }
    public OffsetDateTime approvedAt() { return approvedAt; }
    public UUID approvedBy() { return approvedBy; }
    public OffsetDateTime lockedAt() { return lockedAt; }
    public UUID lockedBy() { return lockedBy; }
    public OffsetDateTime archivedAt() { return archivedAt; }
    public UUID archivedBy() { return archivedBy; }
}
