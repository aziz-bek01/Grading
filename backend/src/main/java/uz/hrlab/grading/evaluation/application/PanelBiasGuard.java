package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.UUID;

/**
 * BE-11 / Defect-2 — bias-isolation read guard (CRITICAL, security R-CRIT-1 /
 * REQ-ISO-1..6, DATA LAYER not UI).
 *
 * <p>A per-evaluator PANEL sheet (an evaluation with a non-null {@code panel_id})
 * and its scores / calibration history are readable ONLY by their OWNING evaluator
 * or by an HRLab oversight role. Evaluator A must NEVER read evaluator B's panel
 * sheet — blind committee scoring. The rule:
 * <ul>
 *   <li>A caller holding an HRLab OVERSIGHT role
 *       ({@link RoleCodes#PANEL_OVERSIGHT_ROLES} — super-admin / consultant /
 *       project-manager) is exempt: those identities are calibration/aggregation
 *       oversight and are NOT panel peers, so they may read any sheet for
 *       calibration.</li>
 *   <li>Otherwise, a single-id read of a PANEL evaluation is allowed ONLY if the
 *       requester is its own evaluator (REQ-ISO-2). A peer attempt is denied with a
 *       404-equivalent {@link TenantAccessDeniedException} (no existence reveal) +
 *       an {@code ACCESS_DENIED_BY_ABAC} denial audit (REQ-ISO-4 / REQ-AUD-2).</li>
 *   <li>Neither {@code EVALUATION_READ} NOR {@code CAMPAIGN_RESULTS_VIEW} lifts the
 *       per-sheet blind (deny-by-default, REQ-ISO-3 + Defect-2). A peer committee
 *       member who happens to hold {@code CAMPAIGN_RESULTS_VIEW} (e.g. a tenant
 *       department-director role granted it) STILL cannot read another evaluator's
 *       sheet — that permission gates only the AGGREGATE / calibration result
 *       surface ({@code PanelQueries.getResult}), never peer per-evaluator
 *       sheets.</li>
 * </ul>
 *
 * <p>The blind is INDEPENDENT of panel phase: a peer cannot read evaluator B's
 * panel sheet whether the panel is still collecting OR already averaged. The
 * post-AVERAGED combined view lives on the separate oversight-gated result read
 * surface (BE-13), not on the per-evaluator sheet endpoints. Panelless / legacy
 * evaluations ({@code panel_id == null}) keep their existing visibility rules and
 * are unaffected (Defect-2 is scoped strictly to panel sheets).
 */
@Component
public class PanelBiasGuard {

    private final AuditService audit;

    public PanelBiasGuard(AuditService audit) {
        this.audit = audit;
    }

    /**
     * Enforce the blind for a single-id evaluation read. No-op for panelless /
     * legacy evaluations ({@code panel_id == null}), the owning evaluator, and
     * HRLab oversight-role holders. Throws {@link TenantAccessDeniedException}
     * (→ 404) for a peer reading another evaluator's PANEL sheet, in ANY panel
     * phase. The caller must already have tenant-matched the evaluation.
     */
    public void enforceCanReadSheet(TenantContext ctx, EvaluationJpaEntity evaluation) {
        UUID panelId = evaluation.getPanelId();
        if (panelId == null) {
            return; // legacy / panelless — no blind (scoped strictly to panel sheets)
        }
        if (canBypassBlind(ctx)) {
            return; // HRLab oversight — may read any sheet for calibration
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
     *         no confinement (HRLab oversight-role holder).
     */
    public boolean shouldConfineGridToOwn(TenantContext ctx) {
        // Deny-by-default: a non-oversight caller is confined whenever the grid MAY
        // contain panel sheets. We cannot cheaply know per-row here, so confinement
        // is applied at the query layer (own-only predicate). An HRLab oversight-role
        // holder is never confined.
        return !canBypassBlind(ctx);
    }

    /**
     * Defect-2: ONLY an HRLab oversight role ({@link RoleCodes#PANEL_OVERSIGHT_ROLES}
     * — super-admin / consultant / project-manager) lifts the per-sheet bias blind.
     *
     * <p>Previously this keyed on the {@code CAMPAIGN_RESULTS_VIEW} permission, which
     * a tenant peer role (department-director) can hold — letting committee members
     * read each other's in-progress sheets (a blind-scoring leak). Oversight roles
     * are NOT panel peers by construction, so they can never leak a co-evaluator's
     * score to a rival evaluator. {@code CAMPAIGN_RESULTS_VIEW} continues to gate the
     * separate AGGREGATE result surface ({@code PanelQueries.getResult}); it no
     * longer bypasses the per-evaluator sheet blind here.
     */
    public boolean canBypassBlind(TenantContext ctx) {
        if (ctx == null || ctx.roles() == null) {
            return false;
        }
        for (String role : RoleCodes.PANEL_OVERSIGHT_ROLES) {
            if (ctx.hasRole(role)) {
                return true;
            }
        }
        return false;
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
                            + ";reason=peer_panel_sheet_blocked")
                    .build());
        } catch (RuntimeException ignored) {
            // Audit failure must not mask the denial.
        }
    }
}
