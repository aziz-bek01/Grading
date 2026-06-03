package uz.hrlab.grading.gradestructure.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.gradestructure.domain.GradeBandGapPolicy;
import uz.hrlab.grading.gradestructure.domain.GradeStructure;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatus;
import uz.hrlab.grading.gradestructure.domain.GradeStructureType;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "grade_structures")
public class GradeStructureJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name_i18n", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> nameI18n = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "description_i18n", columnDefinition = "jsonb")
    private Map<String, String> descriptionI18n = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "structure_type", nullable = false, length = 16)
    private GradeStructureType structureType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private GradeStructureStatus status;

    @Column(name = "version_number", nullable = false)
    private int versionNumber = 1;

    @Column(name = "previous_version_id")
    private UUID previousVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "gap_policy", nullable = false, length = 24)
    private GradeBandGapPolicy gapPolicy = GradeBandGapPolicy.STRICT_NO_GAPS;

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

    protected GradeStructureJpaEntity() { }

    public GradeStructureJpaEntity(UUID id, UUID tenantId, UUID projectId, String code,
                                   GradeStructureType structureType,
                                   GradeStructureStatus status,
                                   int versionNumber, UUID previousVersionId,
                                   GradeBandGapPolicy gapPolicy) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.code = code;
        this.structureType = structureType;
        this.status = status;
        this.versionNumber = versionNumber;
        this.previousVersionId = previousVersionId;
        this.gapPolicy = gapPolicy == null ? GradeBandGapPolicy.STRICT_NO_GAPS : gapPolicy;
    }

    public GradeStructure toDomain() {
        return new GradeStructure(id, tenantId, projectId, code, nameI18n, descriptionI18n,
                structureType, status, versionNumber, previousVersionId, gapPolicy,
                approvedAt, approvedBy, lockedAt, lockedBy, archivedAt, archivedBy);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public String getCode() { return code; }
    public Map<String, String> getNameI18n() { return nameI18n; }
    public Map<String, String> getDescriptionI18n() { return descriptionI18n; }
    public GradeStructureType getStructureType() { return structureType; }
    public GradeStructureStatus getStatus() { return status; }
    public int getVersionNumber() { return versionNumber; }
    public UUID getPreviousVersionId() { return previousVersionId; }
    public GradeBandGapPolicy getGapPolicy() { return gapPolicy; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public UUID getApprovedBy() { return approvedBy; }
    public OffsetDateTime getLockedAt() { return lockedAt; }
    public UUID getLockedBy() { return lockedBy; }
    public OffsetDateTime getArchivedAt() { return archivedAt; }
    public UUID getArchivedBy() { return archivedBy; }

    public void setCode(String v) { this.code = v; }
    public void setNameI18n(Map<String, String> v) { this.nameI18n = nullSafe(v); }
    public void setDescriptionI18n(Map<String, String> v) { this.descriptionI18n = nullSafe(v); }
    public void setStatus(GradeStructureStatus v) { this.status = v; }
    public void setGapPolicy(GradeBandGapPolicy v) {
        this.gapPolicy = v == null ? GradeBandGapPolicy.STRICT_NO_GAPS : v;
    }
    public void setApprovedAt(OffsetDateTime v) { this.approvedAt = v; }
    public void setApprovedBy(UUID v) { this.approvedBy = v; }
    public void setLockedAt(OffsetDateTime v) { this.lockedAt = v; }
    public void setLockedBy(UUID v) { this.lockedBy = v; }
    public void setArchivedAt(OffsetDateTime v) { this.archivedAt = v; }
    public void setArchivedBy(UUID v) { this.archivedBy = v; }

    private static Map<String, String> nullSafe(Map<String, String> in) {
        return in == null ? new HashMap<>() : new HashMap<>(in);
    }
}
