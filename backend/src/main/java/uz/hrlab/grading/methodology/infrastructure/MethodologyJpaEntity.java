package uz.hrlab.grading.methodology.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.methodology.domain.Methodology;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
import uz.hrlab.grading.methodology.domain.MethodologyType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "methodologies")
public class MethodologyJpaEntity extends AuditedJpaEntity {

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
    @Column(name = "methodology_type", nullable = false, length = 32)
    private MethodologyType methodologyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MethodologyStatus status;

    protected MethodologyJpaEntity() { }

    public MethodologyJpaEntity(UUID id, UUID tenantId, UUID projectId, String code,
                                MethodologyType methodologyType, MethodologyStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.code = code;
        this.methodologyType = methodologyType;
        this.status = status;
    }

    public Methodology toDomain() {
        return new Methodology(id, tenantId, projectId, code, nameI18n, descriptionI18n,
                methodologyType, status);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public String getCode() { return code; }
    public Map<String, String> getNameI18n() { return nameI18n; }
    public Map<String, String> getDescriptionI18n() { return descriptionI18n; }
    public MethodologyType getMethodologyType() { return methodologyType; }
    public MethodologyStatus getStatus() { return status; }

    public void setCode(String v) { this.code = v; }
    public void setNameI18n(Map<String, String> v) { this.nameI18n = nullSafe(v); }
    public void setDescriptionI18n(Map<String, String> v) { this.descriptionI18n = nullSafe(v); }
    public void setStatus(MethodologyStatus v) { this.status = v; }
    // methodology_type is editable only while the methodology has no
    // APPROVED/LOCKED version (enforced in UpdateMethodologyMetadataUseCase).
    public void setMethodologyType(MethodologyType v) { this.methodologyType = v; }

    private static Map<String, String> nullSafe(Map<String, String> in) {
        return in == null ? new HashMap<>() : new HashMap<>(in);
    }
}
