package uz.hrlab.grading.organization.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.organization.domain.Department;
import uz.hrlab.grading.organization.domain.DepartmentStatus;
import uz.hrlab.grading.organization.domain.DepartmentType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "departments")
public class DepartmentJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name_i18n", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> nameI18n = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private DepartmentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DepartmentStatus status;

    protected DepartmentJpaEntity() { }

    public DepartmentJpaEntity(UUID id, UUID tenantId, UUID projectId, UUID parentId, String code,
                               Map<String, String> nameI18n, DepartmentType type,
                               DepartmentStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.parentId = parentId;
        this.code = code;
        this.nameI18n = nameI18n == null ? new HashMap<>() : new HashMap<>(nameI18n);
        this.type = type;
        this.status = status;
    }

    public Department toDomain() {
        return new Department(id, tenantId, projectId, parentId, code, nameI18n, type, status);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getParentId() { return parentId; }
    public String getCode() { return code; }
    public Map<String, String> getNameI18n() { return nameI18n; }
    public DepartmentType getType() { return type; }
    public DepartmentStatus getStatus() { return status; }

    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public void setCode(String code) { this.code = code; }
    public void setNameI18n(Map<String, String> nameI18n) { this.nameI18n = nameI18n; }
    public void setType(DepartmentType type) { this.type = type; }
    public void setStatus(DepartmentStatus status) { this.status = status; }
}
