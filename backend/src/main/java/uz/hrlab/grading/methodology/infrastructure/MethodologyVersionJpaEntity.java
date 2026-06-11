package uz.hrlab.grading.methodology.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.ScoringMode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "methodology_versions")
public class MethodologyVersionJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "methodology_id", nullable = false, updatable = false)
    private UUID methodologyId;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MethodologyVersionStatus status;

    // scoring_mode is editable while the version is DRAFT (DRAFT methodologies
    // are fully editable per product design). The DRAFT-only guard lives in the
    // application layer (UpdateMethodologyVersionMetadataUseCase →
    // MethodologyVersionImmutabilityPolicy.ensureMutable); no DB-level guard
    // touches this column. APPROVED/LOCKED/ARCHIVED immutability is preserved by
    // that guard. (Previously updatable=false, which was incidental — not an
    // invariant — and blocked the builder from changing the mode.)
    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_mode", nullable = false, length = 32)
    private ScoringMode scoringMode;

    @Column(name = "target_total_points", precision = 12, scale = 4)
    private BigDecimal targetTotalPoints;

    @Column(name = "previous_version_id", updatable = false)
    private UUID previousVersionId;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "locked_by")
    private UUID lockedBy;

    protected MethodologyVersionJpaEntity() { }

    public MethodologyVersionJpaEntity(UUID id, UUID tenantId, UUID methodologyId,
                                       int versionNumber, MethodologyVersionStatus status,
                                       ScoringMode scoringMode, BigDecimal targetTotalPoints,
                                       UUID previousVersionId) {
        this.id = id;
        this.tenantId = tenantId;
        this.methodologyId = methodologyId;
        this.versionNumber = versionNumber;
        this.status = status;
        this.scoringMode = scoringMode;
        this.targetTotalPoints = targetTotalPoints;
        this.previousVersionId = previousVersionId;
    }

    public MethodologyVersion toDomain() {
        return new MethodologyVersion(id, tenantId, methodologyId, versionNumber, status,
                scoringMode, targetTotalPoints, previousVersionId,
                approvedAt, approvedBy, lockedAt, lockedBy);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getMethodologyId() { return methodologyId; }
    public int getVersionNumber() { return versionNumber; }
    public MethodologyVersionStatus getStatus() { return status; }
    public ScoringMode getScoringMode() { return scoringMode; }
    public BigDecimal getTargetTotalPoints() { return targetTotalPoints; }
    public UUID getPreviousVersionId() { return previousVersionId; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public UUID getApprovedBy() { return approvedBy; }
    public OffsetDateTime getLockedAt() { return lockedAt; }
    public UUID getLockedBy() { return lockedBy; }

    public void setStatus(MethodologyVersionStatus v) { this.status = v; }
    public void setScoringMode(ScoringMode v) { this.scoringMode = v; }
    public void setTargetTotalPoints(BigDecimal v) { this.targetTotalPoints = v; }
    public void setApprovedAt(OffsetDateTime v) { this.approvedAt = v; }
    public void setApprovedBy(UUID v) { this.approvedBy = v; }
    public void setLockedAt(OffsetDateTime v) { this.lockedAt = v; }
    public void setLockedBy(UUID v) { this.lockedBy = v; }
}
