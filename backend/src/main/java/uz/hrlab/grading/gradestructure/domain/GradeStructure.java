package uz.hrlab.grading.gradestructure.domain;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GradeStructure aggregate root — container for {@link Grade} + {@link GradeBand}
 * (architecture §15.3 + ADR-002). Versioning is captured by
 * {@code versionNumber} + {@code previousVersionId}; APPROVED + LOCKED rows are
 * immutable.
 *
 * <p>{@code projectId} = {@code null} marks a global HRLab template visible
 * tenant-wide (mirrors {@code methodologies} convention).
 */
public final class GradeStructure {

    private final UUID id;
    private final UUID tenantId;
    private final UUID projectId;
    private final String code;
    private final Map<String, String> nameI18n;
    private final Map<String, String> descriptionI18n;
    private final GradeStructureType structureType;
    private final GradeStructureStatus status;
    private final int versionNumber;
    private final UUID previousVersionId;
    private final GradeBandGapPolicy gapPolicy;
    private final OffsetDateTime approvedAt;
    private final UUID approvedBy;
    private final OffsetDateTime lockedAt;
    private final UUID lockedBy;
    private final OffsetDateTime archivedAt;
    private final UUID archivedBy;

    public GradeStructure(UUID id, UUID tenantId, UUID projectId, String code,
                          Map<String, String> nameI18n,
                          Map<String, String> descriptionI18n,
                          GradeStructureType structureType,
                          GradeStructureStatus status,
                          int versionNumber, UUID previousVersionId,
                          GradeBandGapPolicy gapPolicy,
                          OffsetDateTime approvedAt, UUID approvedBy,
                          OffsetDateTime lockedAt, UUID lockedBy,
                          OffsetDateTime archivedAt, UUID archivedBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.code = code;
        this.nameI18n = copy(nameI18n);
        this.descriptionI18n = copy(descriptionI18n);
        this.structureType = structureType;
        this.status = status;
        this.versionNumber = versionNumber;
        this.previousVersionId = previousVersionId;
        this.gapPolicy = gapPolicy;
        this.approvedAt = approvedAt;
        this.approvedBy = approvedBy;
        this.lockedAt = lockedAt;
        this.lockedBy = lockedBy;
        this.archivedAt = archivedAt;
        this.archivedBy = archivedBy;
    }

    private static Map<String, String> copy(Map<String, String> src) {
        return src == null ? new HashMap<>() : new HashMap<>(src);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID projectId() { return projectId; }
    public String code() { return code; }
    public Map<String, String> nameI18n() { return nameI18n; }
    public Map<String, String> descriptionI18n() { return descriptionI18n; }
    public GradeStructureType structureType() { return structureType; }
    public GradeStructureStatus status() { return status; }
    public int versionNumber() { return versionNumber; }
    public UUID previousVersionId() { return previousVersionId; }
    public GradeBandGapPolicy gapPolicy() { return gapPolicy; }
    public OffsetDateTime approvedAt() { return approvedAt; }
    public UUID approvedBy() { return approvedBy; }
    public OffsetDateTime lockedAt() { return lockedAt; }
    public UUID lockedBy() { return lockedBy; }
    public OffsetDateTime archivedAt() { return archivedAt; }
    public UUID archivedBy() { return archivedBy; }
}
