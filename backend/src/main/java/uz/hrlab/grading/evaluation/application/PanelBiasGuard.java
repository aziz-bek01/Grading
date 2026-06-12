package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.UUID;

/**
 * BE-11 — bias-isolation read guard (CRITICAL, security R-CRIT-1 / REQ-ISO-1..6,
 * DATA LAYER not UI).
 *
 * <p>While a panel is collecting ({@link uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus#isCollecting()}),
 * evaluator A must NOT read evaluator B's per-evaluator sheet/scores. The rule:
 * <ul>
 *   <li>A caller holding {@link PermissionCodes#CAMPAIGN_RESULTS_VIEW} (HR
 *       director / PM / admin oversight) is exempt — they may read any sheet.</li>
 *   <li>Otherwise, a single-id read of an evaluation that belongs to a still-
 *       collecting panel is allowed ONLY if the requester is its own evaluator
 *       (REQ-ISO-2). A peer attempt is denied with a 404-equivalent
 *       {@link TenantAccessDeniedException} (no existence reveal) + an
 *       {@code ACCESS_DENIED_BY_ABAC} denial audit (REQ-ISO-4 / REQ-AUD-2).</li>
 *   <li>EVALUATION_READ alone NEVER lifts the blind (deny-by-default,
 *       REQ-ISO-3).</li>
 * </ul>
 *
 * <p>Panelless / legacy evaluations (panel_id == null) and panels past the
 * collecting window are unaffected (the per-evaluator breakdown then becomes
 * visible to CAMPAIGN_RESULTS_VIEW holders only; a plain evaluator still sees
 * only their own sheet — but that gating happens in the result read surface,
 * BE-13).
 */
@Component
public class PanelBiasGuard {

    private final PanelRepository panels;
    private final AuditService audit;

    public PanelBiasGuard(PanelRepository panels, AuditService audit) {
        this.panels = panels;
        this.audit = audit;
    }

    /**
     * Enforce the blind for a single-id evaluation read. No-op for panelless
     * evaluations, non-collecting panels, owners, and CAMPAIGN_RESULTS_VIEW
     * holders. Throws {@link TenantAccessDeniedException} (→ 404) for a peer
     * reading a collecting panel's foreign sheet.
     */
    public void enforceCanReadSheet(TenantContext ctx, EvaluationJpaEntity evaluation) {
        if (canBypassBlind(ctx)) {
            return;
        }
        UUID panelId = evaluation.getPanelId();
        if (panelId == null) {
            return; // legacy / panelless — no blind
        }
        EvaluationPanelJpaEntity panel = panels.findByIdAndTenantId(panelId, ctx.tenantId())
                .orElse(null);
        if (panel == null || !panel.getStatus().isCollecting()) {
            return; // not in the collecting window
        }
        boolean isOwner = ctx.userId() != null
                && ctx.userId().equals(evaluation.getEvaluatorUserId());
        if (!isOwner) {
            recordDenial(ctx, evaluation, panelId);
            throw new TenantAccessDeniedException();
        }
    }

    /**
     * Whether the blind applies to the K-sheet GRID for this caller. When true,
     * the grid query must add {@code AND evaluator_user_id = currentUserId} so a
     * peer never even sees another evaluator's row.
     *
     * @return true ⇒ confine the grid to the caller's own evaluations; false ⇒
     *         no confinement (bypass holder, or no collecting panels in scope).
     */
    public boolean shouldConfineGridToOwn(TenantContext ctx) {
        // Deny-by-default: a non-bypass caller is confined whenever the grid MAY
        // contain collecting-panel sheets. We cannot cheaply know per-row here, so
        // confinement is applied at the query layer (own-only predicate). A bypass
        // holder is never confined.
        return !canBypassBlind(ctx);
    }

    /** CAMPAIGN_RESULTS_VIEW lifts the blind; EVALUATION_READ alone does NOT. */
    public boolean canBypassBlind(TenantContext ctx) {
        return ctx.hasPermission(PermissionCodes.CAMPAIGN_RESULTS_VIEW);
    }

    private void recordDenial(TenantContext ctx, EvaluationJpaEntity evaluation, UUID panelId) {
        try {
            audit.record(AuditEvent.builder()
                    .tenantId(ctx.tenantId())
                    .projectId(evaluation.getProjectId())
                    .actorUserId(ctx.userId())
                    .action(AuditAction.ACCESS_DENIED_BY_ABAC)
                    .entityType("Evaluation")
                    .entityId(evaluation.getId())
                    .reason("policy=PanelBiasGuard;panel=" + panelId
                            + ";reason=peer_sheet_blocked_while_collecting")
                    .build());
        } catch (RuntimeException ignored) {
            // Audit failure must not mask the denial.
        }
    }
}
