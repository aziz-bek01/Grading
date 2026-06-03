package uz.hrlab.grading.methodology.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * MethodologyVersion domain model — carries DRAFT/APPROVED/LOCKED/ARCHIVED
 * state machine. Owns the Factor + FactorLevel tree.
 *
 * <p>Approval + lock + edit rules — see
 * {@link MethodologyVersionStatusTransitionPolicy} and
 * {@link MethodologyVersionImmutabilityPolicy}.
 */
public final class MethodologyVersion {

    private final UUID id;
    private final UUID tenantId;
    private final UUID methodologyId;
    private final int versionNumber;
    private final MethodologyVersionStatus status;
    private final ScoringMode scoringMode;
    private final BigDecimal targetTotalPoints;
    private final UUID previousVersionId;
    private final OffsetDateTime approvedAt;
    private final UUID approvedBy;
    private final OffsetDateTime lockedAt;
    private final UUID lockedBy;

    public MethodologyVersion(UUID id, UUID tenantId, UUID methodologyId,
                              int versionNumber, MethodologyVersionStatus status,
                              ScoringMode scoringMode, BigDecimal targetTotalPoints,
                              UUID previousVersionId,
                              OffsetDateTime approvedAt, UUID approvedBy,
                              OffsetDateTime lockedAt, UUID lockedBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.methodologyId = methodologyId;
        this.versionNumber = versionNumber;
        this.status = status;
        this.scoringMode = scoringMode;
        this.targetTotalPoints = targetTotalPoints;
        this.previousVersionId = previousVersionId;
        this.approvedAt = approvedAt;
        this.approvedBy = approvedBy;
        this.lockedAt = lockedAt;
        this.lockedBy = lockedBy;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID methodologyId() { return methodologyId; }
    public int versionNumber() { return versionNumber; }
    public MethodologyVersionStatus status() { return status; }
    public ScoringMode scoringMode() { return scoringMode; }
    public BigDecimal targetTotalPoints() { return targetTotalPoints; }
    public UUID previousVersionId() { return previousVersionId; }
    public OffsetDateTime approvedAt() { return approvedAt; }
    public UUID approvedBy() { return approvedBy; }
    public OffsetDateTime lockedAt() { return lockedAt; }
    public UUID lockedBy() { return lockedBy; }
}
