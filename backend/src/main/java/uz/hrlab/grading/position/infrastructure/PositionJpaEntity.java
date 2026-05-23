package uz.hrlab.grading.position.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.position.domain.Position;
import uz.hrlab.grading.position.domain.PositionStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "positions")
public class PositionJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "title_i18n", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> titleI18n = new HashMap<>();

    @Column(name = "function", length = 200)
    private String function;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "job_family", length = 100)
    private String jobFamily;

    @Column(name = "job_level", length = 100)
    private String jobLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PositionStatus status;

    protected PositionJpaEntity() { }

    public PositionJpaEntity(UUID id, UUID tenantId, UUID projectId, UUID departmentId, String code,
                             Map<String, String> titleI18n, String function, String category,
                             String jobFamily, String jobLevel, PositionStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.departmentId = departmentId;
        this.code = code;
        this.titleI18n = titleI18n == null ? new HashMap<>() : new HashMap<>(titleI18n);
        this.function = function;
        this.category = category;
        this.jobFamily = jobFamily;
        this.jobLevel = jobLevel;
        this.status = status;
    }

    public Position toDomain() {
        return new Position(id, tenantId, projectId, departmentId, code, titleI18n, function,
                category, jobFamily, jobLevel, status);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getDepartmentId() { return departmentId; }
    public String getCode() { return code; }
    public Map<String, String> getTitleI18n() { return titleI18n; }
    public String getFunction() { return function; }
    public String getCategory() { return category; }
    public String getJobFamily() { return jobFamily; }
    public String getJobLevel() { return jobLevel; }
    public PositionStatus getStatus() { return status; }

    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public void setCode(String code) { this.code = code; }
    public void setTitleI18n(Map<String, String> titleI18n) { this.titleI18n = titleI18n; }
    public void setFunction(String function) { this.function = function; }
    public void setCategory(String category) { this.category = category; }
    public void setJobFamily(String jobFamily) { this.jobFamily = jobFamily; }
    public void setJobLevel(String jobLevel) { this.jobLevel = jobLevel; }
    public void setStatus(PositionStatus status) { this.status = status; }
}
