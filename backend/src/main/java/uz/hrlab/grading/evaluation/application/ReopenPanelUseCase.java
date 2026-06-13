package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelFactorAverageRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * BE-12 / Defect-2 BE — reopen a panel: {@code SUBMITTED/AVERAGED ->
 * AWAITING_EVALUATIONS}.
 *
 * <p>Resets the panel so evaluators can re-score, clears the materialized
 * {@code panel_factor_averages} + the stored totals so a fresh average is
 * required (never silently recompute behind the CEO — REQ-AVG-4). The
 * contributing sheets are NOT touched here; the watcher will re-average once the
 * re-scored sheets reach COMPLETE again. (Untouched evaluators keep their
 * COMPLETE state — their immutability is preserved; re-scoring is an explicit
 * per-evaluator action.)
 *
 * <p>There is ONE reopen body ({@link #applyReopen}). Two thin public
 * entrypoints share it:
 * <ul>
 *   <li>{@link #onChangesRequested} — invoked by {@code PanelApprovalOutcomeListener}
 *       on a CEO CHANGES_REQUESTED decision (no event bus). Fail-soft: a missing
 *       panel returns {@code null} so the approval decision is never broken; the
 *       tenant + actor are supplied by the approval engine, not a UI caller, so no
 *       permission re-check is performed here.</li>
 *   <li>{@link #reopen} — the manual controller path (Defect-2 BE). Resolves the
 *       active tenant from {@link TenantContextHolder}, re-checks
 *       {@code EVALUATION_PANEL_MANAGE} (defense in depth on top of the
 *       controller @PreAuthorize), then delegates to the same body.</li>
 * </ul>
 */
@Service
public class ReopenPanelUseCase {

    private final PanelRepository panels;
    private final PanelFactorAverageRepository averages;
    private final AuditService audit;

    public ReopenPanelUseCase(PanelRepository panels,
                              PanelFactorAverageRepository averages,
                              AuditService audit) {
        this.panels = panels;
        this.averages = averages;
        this.audit = audit;
    }

    /**
     * Approval-coupling entrypoint (CEO CHANGES_REQUESTED). Fail-soft — a missing
     * panel returns {@code null} so the approval decision is never broken.
     */
    @Transactional
    public EvaluationPanel onChangesRequested(UUID tenantId, UUID panelId, UUID actorUserId) {
        EvaluationPanelJpaEntity panel = panels.findByIdAndTenantId(panelId, tenantId)
                .orElse(null);
        if (panel == null) {
            return null; // fail-soft — do not break the approval decision
        }
        return applyReopen(tenantId, panel, actorUserId);
    }

    /**
     * Manual controller entrypoint (Defect-2 BE). Resolves the active tenant +
     * actor from the security context and re-checks {@code EVALUATION_PANEL_MANAGE}
     * (defense in depth), then runs the SAME reopen body as the approval coupling.
     * A cross-tenant / unknown id surfaces as a 404 (no existence reveal).
     */
    @Transactional
    public EvaluationPanel reopen(UUID panelId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_PANEL_MANAGE)) {
            throw new PermissionDeniedException();
        }
        EvaluationPanelJpaEntity panel = panels.findByIdAndTenantId(panelId, ctx.tenantId())
                .orElseThrow(uz.hrlab.grading.common.exception.TenantAccessDeniedException::new);
        return applyReopen(ctx.tenantId(), panel, ctx.userId());
    }

    /**
     * The SINGLE reopen body shared by both entrypoints — no duplicate logic.
     * Status guard: only {@code SUBMITTED} / {@code AVERAGED} are reopenable; any
     * other status is a no-op (returns the panel unchanged).
     */
    private EvaluationPanel applyReopen(UUID tenantId, EvaluationPanelJpaEntity panel,
                                        UUID actorUserId) {
        EvaluationPanelStatus status = panel.getStatus();
        if (status != EvaluationPanelStatus.SUBMITTED
                && status != EvaluationPanelStatus.AVERAGED) {
            return panel.toDomain(); // nothing to reopen
        }

        panel.setStatus(EvaluationPanelStatus.AWAITING_EVALUATIONS);
        // Clear stored average + per-factor rows so the next completion recomputes.
        panel.setRawTotalScore(null);
        panel.setDisplayedTotalScore(null);
        panel.setAveragedAt(null);
        panel.setAveragedBy(null);
        panels.save(panel);
        averages.deleteAllByTenantIdAndPanelId(tenantId, panel.getId());

        audit.record(AuditEvent.builder()
                .tenantId(tenantId)
                .projectId(panel.getProjectId())
                .actorUserId(actorUserId)
                .action(AuditAction.EVALUATION_PANEL_REOPENED)
                .entityType("EvaluationPanel")
                .entityId(panel.getId())
                .reason("reopened from " + status)
                .build());
        return panel.toDomain();
    }
}
