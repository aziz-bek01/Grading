package uz.hrlab.grading.evaluation.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.evaluation.migration.BackfillPanelApprovalsMigration;
import uz.hrlab.grading.evaluation.migration.PanelApprovalReconciliationRunner;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * BE-044 — panel OPS/repair endpoints, kept OFF the panel CRUD controller
 * ({@link PanelController}). Same base path so the frontend
 * ({@code endpoints.panels.reconcileApprovals}) is unaffected, but this is an
 * operator/repair surface, not part of the panel lifecycle API.
 *
 * <p>The reconciliation runner is now an unconditional bean (BE-044 dropped its
 * {@code @Profile("!migrate")}), so it is injected as the concrete type directly —
 * no {@code ObjectProvider} indirection. Its boot sweep no-ops under the migrate
 * profile; the on-demand {@code runForTenant} path invoked here only ever runs
 * under the runtime profile.
 */
@RestController
@RequestMapping("/api/v1/panels")
public class PanelOpsController {

    private final PanelApprovalReconciliationRunner reconciliationRunner;

    public PanelOpsController(PanelApprovalReconciliationRunner reconciliationRunner) {
        this.reconciliationRunner = reconciliationRunner;
    }

    /**
     * Ops repair — on-demand trigger for the panel-approval reconciliation against
     * the CALLER's tenant, without waiting for an API restart. Reuses the exact
     * idempotent {@link PanelApprovalReconciliationRunner#runForTenant(UUID)} that
     * runs on boot: it recomputes empty {@code panel_factor_averages} for SUBMITTED
     * panels from their existing scores AND opens the missing {@code EVALUATION_PANEL}
     * CEO approvals (so backfilled panels become signable + show their average).
     *
     * <p>Why this exists separately from the boot sweep: the startup runner visits
     * only ACTIVE tenants, so a non-ACTIVE (e.g. pilot) tenant is silently skipped.
     * This targets the active tenant DIRECTLY (RLS-bound — never another tenant) and
     * is safe to call repeatedly. Gated on {@code EVALUATION_PANEL_MANAGE}.
     */
    @PostMapping("/reconcile-approvals")
    @PreAuthorize("hasAuthority('EVALUATION_PANEL_MANAGE')")
    public ReconcileApprovalsResponse reconcileApprovals() {
        UUID tenantId = TenantContextHolder.requireActive().tenantId();
        BackfillPanelApprovalsMigration.Result r = reconciliationRunner.runForTenant(tenantId);
        return new ReconcileApprovalsResponse(
                tenantId, r.cancelledLegacyApprovals(), r.openedPanelApprovals());
    }
}
