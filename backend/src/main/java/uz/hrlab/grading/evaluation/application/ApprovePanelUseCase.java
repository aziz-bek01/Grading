package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * BE-10 — flip a panel {@code SUBMITTED -> APPROVED} on the CEO's approval
 * decision, the analogue of {@code ApproveEvaluationUseCase}. Invoked by the
 * approval workflow when the last step of the {@code EVALUATION_PANEL} request
 * is approved (coupling is manual, same pattern as evaluation approve — no event
 * bus).
 *
 * <p>On approval it assigns the grade from the panel's averaged
 * {@code raw_total_score} via the existing {@link EvaluationGradeAssignmentService}
 * (reuse unchanged — REQ-CEO-3), and locks the contributing sheets
 * (immutability symmetry).
 *
 * <p>Permission is enforced by the approval step itself
 * ({@code EVALUATION_PANEL_APPROVE}); this is a system-internal transition
 * triggered by an already-authorized decision, so it does NOT re-check a
 * permission here (mirrors how the evaluation approve path trusts the step gate).
 */
@Service
public class ApprovePanelUseCase {

    private final PanelRepository panels;
    private final EvaluationRepository evaluations;
    private final EvaluationGradeAssignmentService gradeAssignment;
    private final AuditService audit;

    public ApprovePanelUseCase(PanelRepository panels,
                               EvaluationRepository evaluations,
                               EvaluationGradeAssignmentService gradeAssignment,
                               AuditService audit) {
        this.panels = panels;
        this.evaluations = evaluations;
        this.gradeAssignment = gradeAssignment;
        this.audit = audit;
    }

    /**
     * @param tenantId    server-derived active tenant
     * @param panelId     the approved panel (entity_id of the approval request)
     * @param actorUserId the deciding CEO (for approved_by + audit)
     */
    @Transactional
    public EvaluationPanel onApproved(UUID tenantId, UUID panelId, UUID actorUserId) {
        EvaluationPanelJpaEntity panel = panels.findByIdAndTenantId(panelId, tenantId)
                .orElse(null);
        // Fail-soft: a stale / missing panel must not break the approval decision.
        if (panel == null || panel.getStatus() != EvaluationPanelStatus.SUBMITTED) {
            return panel == null ? null : panel.toDomain();
        }

        OffsetDateTime now = OffsetDateTime.now();
        panel.setStatus(EvaluationPanelStatus.APPROVED);
        panel.setApprovedAt(now);
        panel.setApprovedBy(actorUserId);
        // Reuse the unchanged grade-band lookup against the averaged score.
        gradeAssignment.assignToPanel(panel, actorUserId);
        panels.save(panel);

        // Immutability symmetry — lock the contributing sheets so a late edit
        // cannot silently change an already-CEO-approved average (REQ-AVG-4).
        for (EvaluationJpaEntity sheet : evaluations.findAllByTenantIdAndPanelId(tenantId, panelId)) {
            if (sheet.getStatus() == EvaluationStatus.COMPLETE) {
                sheet.setStatus(EvaluationStatus.LOCKED);
                sheet.setLockedAt(now);
                sheet.setLockedBy(actorUserId);
                evaluations.save(sheet);
            }
        }

        audit.record(AuditEvent.builder()
                .tenantId(tenantId)
                .projectId(panel.getProjectId())
                .actorUserId(actorUserId)
                .action(AuditAction.EVALUATION_PANEL_APPROVED)
                .entityType("EvaluationPanel")
                .entityId(panelId)
                .reason("displayedTotal=" + panel.getDisplayedTotalScore()
                        + ";grade=" + panel.getAssignedGradeNumber())
                .build());
        return panel.toDomain();
    }
}
