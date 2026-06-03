package uz.hrlab.grading.methodology.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.methodology.domain.FactorLevel;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "factor_levels")
public class FactorLevelJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "factor_id", nullable = false, updatable = false)
    private UUID factorId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "level_order", nullable = false)
    private int levelOrder;

    @Column(name = "points", precision = 12, scale = 4, nullable = false)
    private BigDecimal points = BigDecimal.ZERO;

    @Column(name = "scale_value", precision = 12, scale = 4)
    private BigDecimal scaleValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "label_i18n", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> labelI18n = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "description_i18n", columnDefinition = "jsonb")
    private Map<String, String> descriptionI18n = new HashMap<>();

    protected FactorLevelJpaEntity() { }

    public FactorLevelJpaEntity(UUID id, UUID tenantId, UUID factorId, String code,
                                int levelOrder, BigDecimal points, BigDecimal scaleValue) {
        this.id = id;
        this.tenantId = tenantId;
        this.factorId = factorId;
        this.code = code;
        this.levelOrder = levelOrder;
        this.points = points == null ? BigDecimal.ZERO : points;
        this.scaleValue = scaleValue;
    }

    public FactorLevel toDomain() {
        return new FactorLevel(id, tenantId, factorId, code, levelOrder, points, scaleValue,
                labelI18n, descriptionI18n);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getFactorId() { return factorId; }
    public String getCode() { return code; }
    public int getLevelOrder() { return levelOrder; }
    public BigDecimal getPoints() { return points; }
    public BigDecimal getScaleValue() { return scaleValue; }
    public Map<String, String> getLabelI18n() { return labelI18n; }
    public Map<String, String> getDescriptionI18n() { return descriptionI18n; }

    public void setCode(String v) { this.code = v; }
    public void setLevelOrder(int v) { this.levelOrder = v; }
    public void setPoints(BigDecimal v) { this.points = v == null ? BigDecimal.ZERO : v; }
    public void setScaleValue(BigDecimal v) { this.scaleValue = v; }
    public void setLabelI18n(Map<String, String> v) { this.labelI18n = nullSafe(v); }
    public void setDescriptionI18n(Map<String, String> v) { this.descriptionI18n = nullSafe(v); }

    private static Map<String, String> nullSafe(Map<String, String> in) {
        return in == null ? new HashMap<>() : new HashMap<>(in);
    }
}
