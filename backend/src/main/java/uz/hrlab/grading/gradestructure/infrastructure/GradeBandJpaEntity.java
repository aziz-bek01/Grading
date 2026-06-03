package uz.hrlab.grading.gradestructure.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.gradestructure.domain.GradeBand;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "grade_bands")
public class GradeBandJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "grade_id", nullable = false, updatable = false)
    private UUID gradeId;

    @Column(name = "grade_structure_id", nullable = false, updatable = false)
    private UUID gradeStructureId;

    @Column(name = "min_score", precision = 12, scale = 4, nullable = false)
    private BigDecimal minScore;

    @Column(name = "max_score", precision = 12, scale = 4, nullable = false)
    private BigDecimal maxScore;

    protected GradeBandJpaEntity() { }

    public GradeBandJpaEntity(UUID id, UUID tenantId, UUID gradeId, UUID gradeStructureId,
                              BigDecimal minScore, BigDecimal maxScore) {
        this.id = id;
        this.tenantId = tenantId;
        this.gradeId = gradeId;
        this.gradeStructureId = gradeStructureId;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public GradeBand toDomain() {
        return new GradeBand(id, tenantId, gradeId, minScore, maxScore);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getGradeId() { return gradeId; }
    public UUID getGradeStructureId() { return gradeStructureId; }
    public BigDecimal getMinScore() { return minScore; }
    public BigDecimal getMaxScore() { return maxScore; }

    public void setMinScore(BigDecimal v) { this.minScore = v; }
    public void setMaxScore(BigDecimal v) { this.maxScore = v; }
}
