package uz.hrlab.grading.methodology.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.MethodologyVersionPrimaryLocaleValidator;
import uz.hrlab.grading.methodology.domain.MethodologyWeightValidationPolicy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "factors")
public class FactorJpaEntity extends AuditedJpaEntity
        implements MethodologyWeightValidationPolicy.FactorWeightView,
                   MethodologyVersionPrimaryLocaleValidator.FactorNameView {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "methodology_version_id", nullable = false, updatable = false)
    private UUID methodologyVersionId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name_i18n", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> nameI18n = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "description_i18n", columnDefinition = "jsonb")
    private Map<String, String> descriptionI18n = new HashMap<>();

    @Column(name = "weight", precision = 12, scale = 4, nullable = false)
    private BigDecimal weight = BigDecimal.ZERO;

    @Column(name = "max_points", precision = 12, scale = 4, nullable = false)
    private BigDecimal maxPoints = BigDecimal.ZERO;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "required", nullable = false)
    private boolean required = true;

    protected FactorJpaEntity() { }

    public FactorJpaEntity(UUID id, UUID tenantId, UUID methodologyVersionId, String code,
                           BigDecimal weight, BigDecimal maxPoints, int sortOrder,
                           boolean required) {
        this.id = id;
        this.tenantId = tenantId;
        this.methodologyVersionId = methodologyVersionId;
        this.code = code;
        this.weight = weight == null ? BigDecimal.ZERO : weight;
        this.maxPoints = maxPoints == null ? BigDecimal.ZERO : maxPoints;
        this.sortOrder = sortOrder;
        this.required = required;
    }

    public Factor toDomain() {
        return new Factor(id, tenantId, methodologyVersionId, code, nameI18n, descriptionI18n,
                weight, maxPoints, sortOrder, required);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getMethodologyVersionId() { return methodologyVersionId; }
    @Override public String code() { return code; }
    public String getCode() { return code; }
    public Map<String, String> getNameI18n() { return nameI18n; }
    @Override public Map<String, String> nameI18n() { return nameI18n; }
    public Map<String, String> getDescriptionI18n() { return descriptionI18n; }
    @Override public BigDecimal weight() { return weight; }
    public BigDecimal getWeight() { return weight; }
    public BigDecimal getMaxPoints() { return maxPoints; }
    public int getSortOrder() { return sortOrder; }
    public boolean isRequired() { return required; }

    public void setCode(String v) { this.code = v; }
    public void setNameI18n(Map<String, String> v) { this.nameI18n = nullSafe(v); }
    public void setDescriptionI18n(Map<String, String> v) { this.descriptionI18n = nullSafe(v); }
    public void setWeight(BigDecimal v) { this.weight = v == null ? BigDecimal.ZERO : v; }
    public void setMaxPoints(BigDecimal v) { this.maxPoints = v == null ? BigDecimal.ZERO : v; }
    public void setSortOrder(int v) { this.sortOrder = v; }
    public void setRequired(boolean v) { this.required = v; }

    private static Map<String, String> nullSafe(Map<String, String> in) {
        return in == null ? new HashMap<>() : new HashMap<>(in);
    }
}
