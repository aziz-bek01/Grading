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
import uz.hrlab.grading.methodology.domain.MethodologyTemplateStatus;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.ScoringMode;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tenant-defined (DB-backed) CUSTOM methodology template (Epic E) — backs
 * {@code methodology_templates} (032-create-methodology-templates.yaml).
 *
 * <p>Holds a FROZEN deep copy of a methodology version's factors + levels
 * ({@link #factorsSnapshot}, a jsonb column) so {@code CreateMethodologyFromTemplate}
 * can re-mint a brand-new methodology v1 reproducibly. The i18n columns reuse
 * the SAME {@code @JdbcTypeCode(SqlTypes.JSON)} mapping used by
 * {@link MethodologyJpaEntity} / {@link FactorJpaEntity}.
 *
 * <p>Tenant-scoped: {@code tenant_id} is set from the active {@code TenantContext}
 * on write (never the request body); RLS on the table is defense-in-depth.
 */
@Entity
@Table(name = "methodology_templates")
public class MethodologyTemplateJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 64, updatable = false)
    private String code;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name_i18n", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> nameI18n = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "description_i18n", columnDefinition = "jsonb")
    private Map<String, String> descriptionI18n = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "methodology_type", nullable = false, length = 32, updatable = false)
    private MethodologyType methodologyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_mode", nullable = false, length = 32, updatable = false)
    private ScoringMode scoringMode;

    @Column(name = "target_total_points", precision = 12, scale = 4, updatable = false)
    private BigDecimal targetTotalPoints;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "factors_snapshot", columnDefinition = "jsonb", nullable = false, updatable = false)
    private MethodologyTemplateSnapshot factorsSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MethodologyTemplateStatus status = MethodologyTemplateStatus.ACTIVE;

    protected MethodologyTemplateJpaEntity() { }

    public MethodologyTemplateJpaEntity(UUID id, UUID tenantId, String code,
                                        MethodologyType methodologyType, ScoringMode scoringMode,
                                        BigDecimal targetTotalPoints,
                                        MethodologyTemplateSnapshot factorsSnapshot,
                                        MethodologyTemplateStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.methodologyType = methodologyType;
        this.scoringMode = scoringMode;
        this.targetTotalPoints = targetTotalPoints;
        this.factorsSnapshot = factorsSnapshot;
        this.status = status == null ? MethodologyTemplateStatus.ACTIVE : status;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public Map<String, String> getNameI18n() { return nameI18n; }
    public Map<String, String> getDescriptionI18n() { return descriptionI18n; }
    public MethodologyType getMethodologyType() { return methodologyType; }
    public ScoringMode getScoringMode() { return scoringMode; }
    public BigDecimal getTargetTotalPoints() { return targetTotalPoints; }
    public MethodologyTemplateSnapshot getFactorsSnapshot() { return factorsSnapshot; }
    public MethodologyTemplateStatus getStatus() { return status; }

    public void setNameI18n(Map<String, String> v) { this.nameI18n = nullSafe(v); }
    public void setDescriptionI18n(Map<String, String> v) { this.descriptionI18n = nullSafe(v); }
    public void setStatus(MethodologyTemplateStatus v) { this.status = v; }

    private static Map<String, String> nullSafe(Map<String, String> in) {
        return in == null ? new HashMap<>() : new HashMap<>(in);
    }
}
