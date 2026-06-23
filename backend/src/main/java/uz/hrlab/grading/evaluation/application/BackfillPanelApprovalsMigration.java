package uz.hrlab.grading.evaluation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.approval.application.CancelApprovalRequestUseCase;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestRepository;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * One-time, idempotent, per-tenant data migration that reconciles the CEO
 * approval inbox with the consolidated panel approval flow after the panel
 * backfill ({@code 036-backfill-panels-from-evaluations.yaml}).
 *
 * <h2>What it fixes</h2>
 * The 036 backfill linked legacy single-evaluator {@code evaluations} to a
 * wrapper {@code evaluation_panel} but did NOT touch approvals. Two artefacts
 * remained:
 * <ol>
 *   <li><b>Stale legacy approvals.</b> Evaluations submitted while
 *       {@code panel_id IS NULL} had opened a per-{@code EVALUATION} approval
 *       (requiredPermission {@code EVALUATION_APPROVE}) via
 *       {@code SubmitEvaluationUseCase}. After 036 those evaluations are
 *       panel-wrapped, but the bare {@code EVALUATION} approval is still PENDING
 *       — wrong cards in the CEO inbox.</li>
 *   <li><b>Stuck panels.</b> A wrapper panel landed in {@code SUBMITTED} but no
 *       {@code EVALUATION_PANEL} approval was ever opened for it — those
 *       positions are invisible to the CEO.</li>
 * </ol>
 *
 * <h2>What it does (per tenant)</h2>
 * <ol>
 *   <li><b>Cancel legacy EVALUATION approvals on panel-wrapped evaluations.</b>
 *       For each PENDING {@link ApprovalEntityType#EVALUATION} request whose
 *       target evaluation now has a non-null {@code panel_id}: cancel it via the
 *       EXISTING {@link CancelApprovalRequestUseCase#cancelSystem} (reason
 *       {@code migrated_to_panel_approval}, standard audit). A PENDING request on
 *       a NON-panel evaluation ({@code panel_id IS NULL}) is left untouched.</li>
 *   <li><b>Open the missing EVALUATION_PANEL approval for stuck panels.</b> For
 *       each {@link EvaluationPanelStatus#SUBMITTED} panel with NO active PENDING
 *       {@code EVALUATION_PANEL} request: open one via the SHARED
 *       {@link CeoPanelApprovalOpener#openIfAbsent} — the same collaborator the
 *       live {@code SubmitPanelToCeoUseCase} uses (single step, requiredPermission
 *       {@code EVALUATION_PANEL_APPROVE}). No re-implementation.</li>
 * </ol>
 *
 * <h2>Idempotency</h2>
 * Re-running is a no-op: branch (1) only selects PENDING requests and
 * {@code cancelSystem} no-ops a non-PENDING one; branch (2) is guarded by
 * {@code findActivePendingForEntity} inside the shared opener.
 *
 * <h2>Tenant isolation + RLS</h2>
 * Every read/write is tenant-scoped via {@code TenantContextHolder} + the
 * {@code *AndTenantId} finders (defense-in-depth on top of forced RLS). The
 * orchestrator ({@code PanelApprovalReconciliationRunner}) sets a per-tenant
 * system context BEFORE calling {@link #runForTenant(UUID)}; that method is
 * {@code @Transactional} and invoked across the Spring proxy, so
 * {@code RlsTenantSessionAspect} binds {@code app.tenant_id} on the connection
 * (the commit-91b6590 transaction-boundary contract) and the nested
 * {@code @Transactional} use cases ({@code cancelSystem} / {@code createSystem})
 * join that same transaction with the GUC already bound.
 */
@Service
public class BackfillPanelApprovalsMigration {

    private static final Logger log =
            LoggerFactory.getLogger(BackfillPanelApprovalsMigration.class);

    static final String CANCEL_REASON = "migrated_to_panel_approval";

    private final ApprovalRequestRepository approvalRequests;
    private final EvaluationRepository evaluations;
    private final PanelRepository panels;
    private final CancelApprovalRequestUseCase cancelApprovalRequest;
    private final CeoPanelApprovalOpener ceoApprovalOpener;
    private final PanelCompletionChecker completionChecker;
    private final ComputePanelAverageUseCase computeAverage;
    private final SubmitPanelToCeoUseCase submitToCeo;

    public BackfillPanelApprovalsMigration(ApprovalRequestRepository approvalRequests,
                                           EvaluationRepository evaluations,
                                           PanelRepository panels,
                                           CancelApprovalRequestUseCase cancelApprovalRequest,
                                           CeoPanelApprovalOpener ceoApprovalOpener,
                                           PanelCompletionChecker completionChecker,
                                           ComputePanelAverageUseCase computeAverage,
                                           SubmitPanelToCeoUseCase submitToCeo) {
        this.approvalRequests = approvalRequests;
        this.evaluations = evaluations;
        this.panels = panels;
        this.cancelApprovalRequest = cancelApprovalRequest;
        this.ceoApprovalOpener = ceoApprovalOpener;
        this.completionChecker = completionChecker;
        this.computeAverage = computeAverage;
        this.submitToCeo = submitToCeo;
    }

    /** Counts of the two actions performed (for the run log + tests). */
    public record Result(int cancelledLegacyApprovals, int openedPanelApprovals) { }

    /**
     * Run BOTH reconciliation branches for the active tenant in ONE transaction.
     *
     * <p>{@code REQUIRES_NEW} so each tenant gets its own committed unit of work
     * (one tenant's failure cannot poison another's) and the RLS aspect binds the
     * GUC for THIS tenant's connection. The orchestrator must have set the active
     * {@link uz.hrlab.grading.tenancy.application.TenantContext} for {@code tenantId}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result runForTenant(UUID tenantId) {
        // Anti-BOLA: the context tenant is authoritative; ignore any mismatch.
        var ctx = TenantContextHolder.requireActive();
        UUID active = ctx.tenantId();
        if (!active.equals(tenantId)) {
            throw new IllegalStateException("tenant context mismatch in panel-approval migration");
        }

        int cancelled = cancelLegacyEvaluationApprovals(active);
        int opened = advanceFullyEvaluatedPanels(active, ctx.userId());

        if (cancelled > 0 || opened > 0) {
            log.info("PanelApprovalReconciliation tenant={} cancelledLegacy={} openedPanel={}",
                    active, cancelled, opened);
        }
        return new Result(cancelled, opened);
    }

    /**
     * Branch (1): retire every PENDING legacy {@code EVALUATION} approval whose
     * target evaluation is now panel-wrapped. Reuses
     * {@link CancelApprovalRequestUseCase#cancelSystem} — no hand-rolled status flip.
     */
    private int cancelLegacyEvaluationApprovals(UUID tenantId) {
        int cancelled = 0;
        for (ApprovalRequestJpaEntity req : approvalRequests
                .findAllByTenantIdAndEntityTypeAndCurrentStatus(
                        tenantId, ApprovalEntityType.EVALUATION, ApprovalRequestStatus.PENDING)) {
            EvaluationJpaEntity evaluation = evaluations
                    .findByIdAndTenantId(req.getEntityId(), tenantId)
                    .orElse(null);
            // Only cancel when the evaluation now belongs to a panel; a genuine
            // single-evaluator (panel_id IS NULL) approval is left untouched.
            if (evaluation == null || evaluation.getPanelId() == null) {
                continue;
            }
            if (cancelApprovalRequest.cancelSystem(req.getId(), CANCEL_REASON) != null) {
                cancelled++;
            }
        }
        return cancelled;
    }

    /**
     * Branch (2): advance EVERY panel that is fully evaluated but never reached the
     * CEO to a PENDING {@code EVALUATION_PANEL} approval — reusing the SAME services
     * the live completion flow uses (no duplication):
     * <ol>
     *   <li>{@code AWAITING_EVALUATIONS} whose roster is fully complete
     *       ({@link PanelCompletionChecker#allActiveComplete}) →
     *       {@link ComputePanelAverageUseCase#compute} (→ {@code AVERAGED}) →
     *       {@link SubmitPanelToCeoUseCase#submitSystem} (→ {@code SUBMITTED} +
     *       approval). Covers panels whose averaging never fired — the watcher only
     *       runs on a score recompute, so a panel completed via another path stays
     *       stuck.</li>
     *   <li>{@code AVERAGED} → {@code submitSystem} (→ {@code SUBMITTED} + approval).
     *       Covers panels whose auto-submit was swallowed fail-soft.</li>
     *   <li>{@code SUBMITTED} with no PENDING request →
     *       {@link CeoPanelApprovalOpener#openIfAbsent}.</li>
     * </ol>
     * Per-panel fail-soft (one panel cannot abort the tenant sweep) and idempotent
     * (each step's guard makes a re-run a no-op). Lists are re-queried per status so
     * a panel advanced in (1)/(2) is simply a no-op when (3) revisits it.
     */
    private int advanceFullyEvaluatedPanels(UUID tenantId, UUID actorUserId) {
        int advanced = 0;
        // (1) fully scored but never averaged.
        for (EvaluationPanelJpaEntity panel : panels
                .findAllByTenantIdAndStatus(tenantId, EvaluationPanelStatus.AWAITING_EVALUATIONS)) {
            if (!completionChecker.allActiveComplete(tenantId, panel.getId())) {
                continue;
            }
            try {
                computeAverage.compute(tenantId, panel.getId(), actorUserId);
                submitToCeo.submitSystem(panel.getId(), actorUserId);
                advanced++;
            } catch (RuntimeException e) {
                log.warn("PanelApprovalReconciliation: advance AWAITING panel {} failed: {}",
                        panel.getId(), e.getClass().getSimpleName(), e);
            }
        }
        // (2) averaged but never submitted (auto-submit fail-soft swallow).
        for (EvaluationPanelJpaEntity panel : panels
                .findAllByTenantIdAndStatus(tenantId, EvaluationPanelStatus.AVERAGED)) {
            try {
                submitToCeo.submitSystem(panel.getId(), actorUserId);
                advanced++;
            } catch (RuntimeException e) {
                log.warn("PanelApprovalReconciliation: submit AVERAGED panel {} failed: {}",
                        panel.getId(), e.getClass().getSimpleName(), e);
            }
        }
        // (3) submitted but the CEO approval was never opened.
        for (EvaluationPanelJpaEntity panel : panels
                .findAllByTenantIdAndStatus(tenantId, EvaluationPanelStatus.SUBMITTED)) {
            if (ceoApprovalOpener.openIfAbsent(tenantId, panel) != null) {
                advanced++;
            }
        }
        return advanced;
    }
}
