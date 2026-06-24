package uz.hrlab.grading.evaluation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;
import uz.hrlab.grading.tenancy.domain.TenantStatus;
import uz.hrlab.grading.tenancy.infrastructure.TenantJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.util.Set;
import java.util.UUID;

/**
 * One-shot orchestrator for {@link BackfillPanelApprovalsMigration} — runs the
 * per-tenant CEO-approval reconciliation automatically on API startup.
 *
 * <h2>Why an {@code ApplicationRunner} in the API runtime (orchestration choice)</h2>
 * The migration must REUSE the existing {@code @Transactional} use cases
 * ({@code CancelApprovalRequestUseCase.cancelSystem}, {@code CeoPanelApprovalOpener}
 * → {@code CreateApprovalRequestUseCase.createSystem}) and must run under the
 * SAME RLS contract as production reads (forced RLS as {@code grading_runtime},
 * GUC bound only inside an aspect-advised transaction — commit 91b6590). A
 * Liquibase {@code customChange} runs in the DDL-role ({@code grading_migrator})
 * migrator JVM OUTSIDE Spring's transaction/RLS aspect, so it could neither bind
 * the GUC the same way nor cleanly invoke those proxied use cases. This runner
 * runs in the API context, so:
 * <ul>
 *   <li>it reuses the EXISTING tenant-iteration + system-context pattern
 *       ({@code WorkerReQueuer}): iterate the RLS-exempt control-plane
 *       {@code public.tenants}, set a minimal per-tenant system
 *       {@link TenantContext}, then call the {@code @Transactional}
 *       {@link BackfillPanelApprovalsMigration#runForTenant} ACROSS the Spring
 *       proxy so {@code RlsTenantSessionAspect} binds {@code app.tenant_id};</li>
 *   <li>it runs automatically on the next deploy (no operator action);</li>
 *   <li>it is safe to run repeatedly (the migration is idempotent), so a restart
 *       re-running it is harmless.</li>
 * </ul>
 *
 * <p>Excluded from the one-shot {@code migrate} profile (mirrors
 * {@code WorkerReQueuer}/{@code WorkerSchedulingConfig}): the DDL migrator JVM
 * runs Liquibase only and must exit; it also connects as {@code grading_migrator}
 * (not the RLS-enforcing runtime role). A kill-switch
 * ({@code grading.migration.panel-approval-reconciliation.enabled}, default
 * {@code true}) lets operators disable it without a code change once it has run.
 */
@Component
@Profile("!migrate")
public class PanelApprovalReconciliationRunner implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(PanelApprovalReconciliationRunner.class);

    private final TenantRepository tenants;
    private final BackfillPanelApprovalsMigration migration;
    private final boolean enabled;

    public PanelApprovalReconciliationRunner(
            TenantRepository tenants,
            BackfillPanelApprovalsMigration migration,
            @Value("${grading.migration.panel-approval-reconciliation.enabled:true}")
            boolean enabled) {
        this.tenants = tenants;
        this.migration = migration;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("PanelApprovalReconciliation disabled by configuration — skipping");
            return;
        }
        int totalCancelled = 0;
        int totalOpened = 0;
        int tenantsTouched = 0;
        for (TenantJpaEntity tenant : tenants.findAll()) {
            if (tenant.getStatus() != TenantStatus.ACTIVE) {
                continue;
            }
            try {
                BackfillPanelApprovalsMigration.Result r = runForTenant(tenant.getId());
                totalCancelled += r.cancelledLegacyApprovals();
                totalOpened += r.openedPanelApprovals();
                if (r.cancelledLegacyApprovals() > 0 || r.openedPanelApprovals() > 0) {
                    tenantsTouched++;
                }
            } catch (RuntimeException e) {
                // One tenant's failure must not abort the whole sweep.
                log.warn("PanelApprovalReconciliation failed for tenant {}: {}",
                        tenant.getId(), e.getClass().getSimpleName(), e);
            }
        }
        log.info("PanelApprovalReconciliation complete: tenantsTouched={} cancelledLegacy={} "
                + "openedPanel={}", tenantsTouched, totalCancelled, totalOpened);
    }

    /**
     * Establish a minimal per-tenant system context (RLS binding only — carries
     * NO permissions, so ABAC stays closed and only the system-path use cases run)
     * and invoke the {@code @Transactional} migration across the Spring proxy.
     * Restores any previously-held context afterwards. {@code public} so it serves
     * both the boot sweep AND the on-demand ops endpoint
     * ({@code POST /api/v1/panels/reconcile-approvals}), which targets one tenant
     * directly — reconciling even a tenant the boot sweep skips (it visits only
     * ACTIVE tenants). The integration test also drives a single tenant through it.
     */
    public BackfillPanelApprovalsMigration.Result runForTenant(UUID tenantId) {
        TenantContext previous = TenantContextHolder.get();
        TenantContextHolder.set(systemContext(tenantId));
        try {
            return migration.runForTenant(tenantId);
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
    }

    /**
     * Minimal system context for RLS binding only — no user, no permissions. The
     * migration calls only the {@code *System} use-case variants, which skip the
     * permission/ABAC gates, so an empty permission set is correct (and keeps ABAC
     * fail-closed by construction). Mirrors {@code WorkerReQueuer.systemContext}.
     */
    private static TenantContext systemContext(UUID tenantId) {
        return new TenantContext(
                null, tenantId, Set.of(), Set.of(), Set.of(), Set.of(), false, null);
    }
}
