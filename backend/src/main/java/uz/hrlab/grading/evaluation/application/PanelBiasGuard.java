package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
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
 *       project-manager) is exempt ONLY when they are a PURE overseer: an oversight
 *       identity that is NOT itself a member/evaluator of the target panel may read
 *       any sheet for calibration. Defect-3: an oversight-role holder who IS a
 *       member/evaluator of the SAME panel is a PEER — he is treated like any other
 *       committee member and is BLIND to a co-evaluator's sheet (so a super-admin
 *       who is also scoring cannot peek at a rival's in-progress sheet).</li>
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
    private final EvaluationRepository evaluations;

    public PanelBiasGuard(AuditService audit, EvaluationRepository evaluations) {
        this.audit = audit;
        this.evaluations = evaluations;
    }

    /**
     * Enforce the blind for a single-id evaluation read. No-op for panelless /
     * legacy evaluations ({@code panel_id == null}), the owning evaluator, and a
     * PURE HRLab overseer (oversight role that is NOT a member of this panel).
     * Throws {@link TenantAccessDeniedException} (→ 404) for a peer reading another
     * evaluator's PANEL sheet, in ANY panel phase — INCLUDING an oversight-role
     * holder who is also a member/evaluator of THIS panel (Defect-3). The caller
     * must already have tenant-matched the evaluation.
     */
    public void enforceCanReadSheet(TenantContext ctx, EvaluationJpaEntity evaluation) {
        UUID panelId = evaluation.getPanelId();
        if (panelId == null) {
            return; // legacy / panelless — no blind (scoped strictly to panel sheets)
        }
        boolean isOwner = ctx.userId() != null
                && ctx.userId().equals(evaluation.getEvaluatorUserId());
        if (isOwner) {
            return; // the owning evaluator always reads their own sheet
        }
        if (canBypassBlind(ctx) && !isPanelMember(ctx, panelId)) {
            // Defect-3: only a PURE overseer (oversight role, NOT a member of this
            // panel) keeps the per-sheet calibration bypass. An oversight holder who
            // is ALSO a member/evaluator of THIS panel is a peer → blind to a
            // co-evaluator's sheet (REQ blind-scoring), falls through to the denial.
            return;
        }
        recordDenial(ctx, evaluation, panelId);
        throw new TenantAccessDeniedException();
    }

    /**
     * Defect-3 peer test — whether the caller has at least one of their OWN
     * evaluations in {@code panelId}. If so they are a member/evaluator of that
     * panel and must be blind to a co-evaluator's sheet even if they hold an HRLab
     * oversight role. Tenant-scoped existence check (any status counts: membership,
     * not progress, is the question).
     */
    private boolean isPanelMember(TenantContext ctx, UUID panelId) {
        return ctx.userId() != null
                && evaluations.existsByTenantIdAndPanelIdAndEvaluatorUserId(
                        ctx.tenantId(), panelId, ctx.userId());
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
            audit.record(AuditEvent.builder(ctx)
                    .projectId(evaluation.getProjectId())
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
