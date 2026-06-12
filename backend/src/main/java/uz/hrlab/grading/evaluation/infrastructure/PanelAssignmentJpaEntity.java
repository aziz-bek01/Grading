package uz.hrlab.grading.evaluation.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;
import uz.hrlab.grading.evaluation.domain.PanelAssignment;
import uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One evaluator's seat on a panel + their role. Changelog 034. */
@Entity
@Table(name = "panel_assignments")
public class PanelAssignmentJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "panel_id", nullable = false, updatable = false)
    private UUID panelId;

    @Column(name = "evaluator_user_id", nullable = false, updatable = false)
    private UUID evaluatorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluator_role", nullable = false, length = 40)
    private EvaluatorRole evaluatorRole;

    // Null until the evaluator's per-evaluator row is created on roster lock.
    @Column(name = "evaluation_id")
    private UUID evaluationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_status", nullable = false, length = 24)
    private PanelAssignmentStatus assignmentStatus;

    protected PanelAssignmentJpaEntity() { }

    public PanelAssignmentJpaEntity(UUID id, UUID tenantId, UUID panelId, UUID evaluatorUserId,
                                    EvaluatorRole evaluatorRole, PanelAssignmentStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.panelId = panelId;
        this.evaluatorUserId = evaluatorUserId;
        this.evaluatorRole = evaluatorRole;
        this.assignmentStatus = status;
    }

    public PanelAssignment toDomain() {
        return new PanelAssignment(id, tenantId, panelId, evaluatorUserId, evaluatorRole,
                evaluationId, assignmentStatus, getUpdatedAt());
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPanelId() { return panelId; }
    public UUID getEvaluatorUserId() { return evaluatorUserId; }
    public EvaluatorRole getEvaluatorRole() { return evaluatorRole; }
    public UUID getEvaluationId() { return evaluationId; }
    public PanelAssignmentStatus getAssignmentStatus() { return assignmentStatus; }

    public void setEvaluatorRole(EvaluatorRole v) { this.evaluatorRole = v; }
    public void setEvaluationId(UUID v) { this.evaluationId = v; }
    public void setAssignmentStatus(PanelAssignmentStatus v) { this.assignmentStatus = v; }
}
