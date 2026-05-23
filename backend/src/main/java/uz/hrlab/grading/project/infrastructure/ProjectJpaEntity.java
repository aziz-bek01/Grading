package uz.hrlab.grading.project.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.project.domain.Project;
import uz.hrlab.grading.project.domain.ProjectStatus;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistence representation of {@code projects} (database-blueprint §5.2,
 * Phase 2 implementation note). Lives in the tenant-data area; in MVP 1
 * shared-schema mode this resolves to {@code public.projects}.
 */
@Entity
@Table(name = "projects")
public class ProjectJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name_i18n", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> nameI18n = new HashMap<>();

    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ProjectStatus status;

    @Column(name = "methodology_version_id")
    private UUID methodologyVersionId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    protected ProjectJpaEntity() { }

    public ProjectJpaEntity(UUID id, UUID tenantId, String code, Map<String, String> nameI18n,
                            String description, ProjectStatus status, UUID methodologyVersionId,
                            LocalDate startDate, LocalDate endDate) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.nameI18n = nameI18n == null ? new HashMap<>() : new HashMap<>(nameI18n);
        this.description = description;
        this.status = status;
        this.methodologyVersionId = methodologyVersionId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Project toDomain() {
        return new Project(id, tenantId, code, nameI18n, description, status,
                methodologyVersionId, startDate, endDate);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public Map<String, String> getNameI18n() { return nameI18n; }
    public String getDescription() { return description; }
    public ProjectStatus getStatus() { return status; }
    public UUID getMethodologyVersionId() { return methodologyVersionId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }

    public void setCode(String code) { this.code = code; }
    public void setNameI18n(Map<String, String> nameI18n) { this.nameI18n = nameI18n; }
    public void setDescription(String description) { this.description = description; }
    public void setStatus(ProjectStatus status) { this.status = status; }
    public void setMethodologyVersionId(UUID id) { this.methodologyVersionId = id; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
