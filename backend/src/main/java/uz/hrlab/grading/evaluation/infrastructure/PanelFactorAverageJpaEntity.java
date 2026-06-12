package uz.hrlab.grading.evaluation.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.evaluation.domain.PanelFactorAverage;

import java.math.BigDecimal;
import java.util.UUID;

/** Materialized per-factor average for a panel (display + reproducibility). Changelog 034. */
@Entity
@Table(name = "panel_factor_averages")
public class PanelFactorAverageJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "panel_id", nullable = false, updatable = false)
    private UUID panelId;

    @Column(name = "factor_id", nullable = false, updatable = false)
    private UUID factorId;

    @Column(name = "avg_raw_factor_score", precision = 12, scale = 4, nullable = false)
    private BigDecimal avgRawFactorScore;

    @Column(name = "evaluator_count", nullable = false)
    private int evaluatorCount;

    protected PanelFactorAverageJpaEntity() { }

    public PanelFactorAverageJpaEntity(UUID id, UUID tenantId, UUID panelId, UUID factorId,
                                       BigDecimal avgRawFactorScore, int evaluatorCount) {
        this.id = id;
        this.tenantId = tenantId;
        this.panelId = panelId;
        this.factorId = factorId;
        this.avgRawFactorScore = avgRawFactorScore;
        this.evaluatorCount = evaluatorCount;
    }

    public PanelFactorAverage toDomain() {
        return new PanelFactorAverage(id, tenantId, panelId, factorId,
                avgRawFactorScore, evaluatorCount);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPanelId() { return panelId; }
    public UUID getFactorId() { return factorId; }
    public BigDecimal getAvgRawFactorScore() { return avgRawFactorScore; }
    public int getEvaluatorCount() { return evaluatorCount; }

    public void setAvgRawFactorScore(BigDecimal v) { this.avgRawFactorScore = v; }
    public void setEvaluatorCount(int v) { this.evaluatorCount = v; }
}
