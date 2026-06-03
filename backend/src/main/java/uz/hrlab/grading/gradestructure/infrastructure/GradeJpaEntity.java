package uz.hrlab.grading.gradestructure.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.gradestructure.domain.Grade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "grades")
public class GradeJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "grade_structure_id", nullable = false, updatable = false)
    private UUID gradeStructureId;

    @Column(name = "grade_number", nullable = false)
    private int gradeNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name_i18n", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> nameI18n = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "description_i18n", columnDefinition = "jsonb")
    private Map<String, String> descriptionI18n = new HashMap<>();

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected GradeJpaEntity() { }

    public GradeJpaEntity(UUID id, UUID tenantId, UUID gradeStructureId,
                          int gradeNumber, int sortOrder) {
        this.id = id;
        this.tenantId = tenantId;
        this.gradeStructureId = gradeStructureId;
        this.gradeNumber = gradeNumber;
        this.sortOrder = sortOrder;
    }

    public Grade toDomain() {
        return new Grade(id, tenantId, gradeStructureId, gradeNumber,
                nameI18n, descriptionI18n, sortOrder);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getGradeStructureId() { return gradeStructureId; }
    public int getGradeNumber() { return gradeNumber; }
    public Map<String, String> getNameI18n() { return nameI18n; }
    public Map<String, String> getDescriptionI18n() { return descriptionI18n; }
    public int getSortOrder() { return sortOrder; }

    public void setGradeNumber(int v) { this.gradeNumber = v; }
    public void setNameI18n(Map<String, String> v) { this.nameI18n = nullSafe(v); }
    public void setDescriptionI18n(Map<String, String> v) { this.descriptionI18n = nullSafe(v); }
    public void setSortOrder(int v) { this.sortOrder = v; }

    private static Map<String, String> nullSafe(Map<String, String> in) {
        return in == null ? new HashMap<>() : new HashMap<>(in);
    }
}
