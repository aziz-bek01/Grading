package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Defect-2 BE — soft cancel (archive) a working evaluation panel.
 *
 * <p>Mirrors {@link ArchiveEvaluationUseCase} (panel level): require
 * {@code EVALUATION_PANEL_MANAGE}; reason ≥ 5 chars; load tenant-scoped via
 * {@link PanelLoader#requirePanel(UUID, UUID)} (cross-tenant id → 404, no
 * existence reveal); enforce the ABAC department write-gate through the panel's
 * position ({@link PanelLoader#requirePosition}). Sets {@code status = ARCHIVED}
 * + {@code archivedAt = now} + {@code archivedBy = ctx.userId()} and saves.
 *
 * <p><b>Guard:</b> only the working statuses {@code AWAITING_EVALUATIONS},
 * {@code AVERAGED}, {@code SUBMITTED} are archivable (see
 * {@link EvaluationPanelStatus#isArchivable()}). The committed/terminal statuses
 * {@code APPROVED}, {@code LOCKED}, {@code ARCHIVED} are rejected with a
 * {@link ValidationException} carrying the stable code {@code PANEL_NOT_ARCHIVABLE}
 * (a CEO-approved/locked panel is immutable). {@code COLLECTING} uses the hard
 * {@link DeletePanelUseCase} path instead.
 *
 * <p>Archiving sets {@code status = ARCHIVED}, which frees the partial-unique
 * active-panel slot ({@code uq_panels_active_per_position_version}) so a fresh
 * panel can be created for the same (position, methodology version) — that is the
 * point of archive while preserving the audit trail.
 */
@Service
public class ArchivePanelUseCase {

    private static final int MIN_REASON_LENGTH = 5;
    /** Stable error code surfaced to the FE for a non-archivable archive attempt. */
    static final String NOT_ARCHIVABLE_CODE = "PANEL_NOT_ARCHIVABLE";

    private final PanelLoader loader;
    private final PanelRepository panels;
    private final AbacGate abacGate;
    private final AuditService audit;

    public ArchivePanelUseCase(PanelLoader loader,
                               PanelRepository panels,
                               AbacGate abacGate,
                               AuditService audit) {
        this.loader = loader;
        this.panels = panels;
        this.abacGate = abacGate;
        this.audit = audit;
    }

    @Transactional
    public EvaluationPanel archive(UUID panelId, String reason) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.EVALUATION_PANEL_MANAGE);
        if (reason == null || reason.trim().length() < MIN_REASON_LENGTH) {
            throw new ValidationException(
                    "reason is required (min " + MIN_REASON_LENGTH + " chars)");
        }
        EvaluationPanelJpaEntity panel = loader.requirePanel(panelId, ctx.tenantId());
        PositionJpaEntity position = loader.requirePosition(panel, ctx.tenantId());
        abacGate.enforceCanWriteInDepartment(
                ctx, panel.getProjectId(), position.getDepartmentId());

        if (!panel.getStatus().isArchivable()) {
            // APPROVED / LOCKED / ARCHIVED are immutable; COLLECTING uses delete.
            throw new ValidationException(NOT_ARCHIVABLE_CODE,
                    "Only AWAITING_EVALUATIONS / AVERAGED / SUBMITTED panels can be archived");
        }

        panel.setStatus(EvaluationPanelStatus.ARCHIVED);
        panel.setArchivedAt(OffsetDateTime.now());
        panel.setArchivedBy(ctx.userId());
        panels.save(panel);

        audit.record(AuditEvent.builder(ctx)
                .projectId(panel.getProjectId())
                .action(AuditAction.EVALUATION_PANEL_ARCHIVED)
                .entityType("EvaluationPanel")
                .entityId(panel.getId())
                .reason(reason)
                .build());
        return panel.toDomain();
    }
}
