package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelAssignmentRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * Defect-2 BE — hard delete a {@code COLLECTING}-only evaluation panel.
 *
 * <p>Mirrors {@link DeleteEvaluationUseCase} exactly (panel level): require
 * {@code EVALUATION_PANEL_MANAGE}; reason ≥ 5 chars; load tenant-scoped via
 * {@link PanelLoader#requirePanel(UUID, UUID)} so a cross-tenant id surfaces as a
 * 404 {@code TenantAccessDeniedException} (no existence reveal); enforce the ABAC
 * department write-gate via {@link PanelLoader#requirePosition} (a panel inherits
 * its department from its position, exactly like an evaluation —
 * {@code CreatePanelUseCase}). Unlike archive this physically removes the row.
 *
 * <p><b>Hard guard:</b> only a {@code COLLECTING} panel may be deleted (see
 * {@link uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus#isDeletable()},
 * mirroring {@link uz.hrlab.grading.evaluation.domain.EvaluationStatus#isDeletable()}).
 * A panel that has moved past COLLECTING keeps its history and uses the ARCHIVE
 * path ({@link ArchivePanelUseCase}); it is rejected here with a
 * {@link ValidationException} carrying the stable code {@code PANEL_NOT_DELETABLE}
 * (400), mirroring evaluation's {@code EVALUATION_NOT_DELETABLE} naming.
 *
 * <p>Dependent {@code panel_assignments} roster rows are deleted FIRST so the
 * parent row never orphans a child FK (deterministic even though the ON DELETE
 * CASCADE FK would also cover it — same spirit as DeleteEvaluation clearing score
 * rows first). Removing a {@code COLLECTING} panel ALSO frees the partial-unique
 * active-panel slot ({@code uq_panels_active_per_position_version}) so a fresh
 * panel can be created for the same (position, methodology version).
 */
@Service
public class DeletePanelUseCase {

    private static final int MIN_REASON_LENGTH = 5;
    /** Stable error code surfaced to the FE for a non-COLLECTING delete attempt. */
    static final String NOT_DELETABLE_CODE = "PANEL_NOT_DELETABLE";

    private final PanelLoader loader;
    private final PanelRepository panels;
    private final PanelAssignmentRepository assignments;
    private final AbacGate abacGate;
    private final AuditService audit;

    public DeletePanelUseCase(PanelLoader loader,
                              PanelRepository panels,
                              PanelAssignmentRepository assignments,
                              AbacGate abacGate,
                              AuditService audit) {
        this.loader = loader;
        this.panels = panels;
        this.assignments = assignments;
        this.abacGate = abacGate;
        this.audit = audit;
    }

    @Transactional
    public void delete(UUID panelId, String reason) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_PANEL_MANAGE)) {
            throw new PermissionDeniedException();
        }
        if (reason == null || reason.trim().length() < MIN_REASON_LENGTH) {
            throw new ValidationException(
                    "reason is required (min " + MIN_REASON_LENGTH + " chars)");
        }
        EvaluationPanelJpaEntity panel = loader.requirePanel(panelId, ctx.tenantId());
        PositionJpaEntity position = loader.requirePosition(panel, ctx.tenantId());
        abacGate.enforceCanWriteInDepartment(
                ctx, panel.getProjectId(), position.getDepartmentId());

        if (!panel.getStatus().isDeletable()) {
            // Past COLLECTING — keeps its history; use ARCHIVE, not delete.
            throw new ValidationException(NOT_DELETABLE_CODE,
                    "Only a COLLECTING panel can be deleted; archive instead");
        }

        // Remove dependent roster seats first (deterministic; FK also cascades).
        assignments.deleteAllByTenantIdAndPanelId(ctx.tenantId(), panel.getId());
        panels.deleteByIdAndTenantId(panel.getId(), ctx.tenantId());

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(panel.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.EVALUATION_PANEL_DELETED)
                .entityType("EvaluationPanel")
                .entityId(panel.getId())
                .reason(reason)
                .build());
    }
}
