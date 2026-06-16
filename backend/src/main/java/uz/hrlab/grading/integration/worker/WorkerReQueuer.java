package uz.hrlab.grading.integration.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.metrics.WorkerMetrics;
import uz.hrlab.grading.reporting.infrastructure.ReportGenerationJob;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;
import uz.hrlab.grading.tenancy.domain.TenantStatus;
import uz.hrlab.grading.tenancy.infrastructure.TenantJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Scheduled re-queuer for the import / export / report async workers
 * (Batch-4, decision D6 — keep the in-process {@code ThreadPoolTaskExecutor},
 * add this periodic re-dispatcher; no message broker).
 *
 * <h2>What it does</h2>
 * Every tick it finds rows in the retryable {@code FAILED} state that are DUE
 * ({@code next_attempt_at <= now} or null) and still UNDER the retry bound, and
 * re-dispatches them through the SAME {@code @Async} workers used by the
 * after-commit path. Rows in the terminal {@code DEAD_LETTER} state are never
 * selected, so there is no infinite loop and no silent drop.
 *
 * <h2>Tenant scoping under forced RLS</h2>
 * This runs on a scheduler thread with NO request context, so
 * {@code TenantContextHolder.get()} is null and forced RLS would hide every
 * row. We therefore iterate the control-plane {@code public.tenants} catalogue
 * (RLS-exempt) and, for each ACTIVE tenant, establish a minimal system
 * {@link TenantContext} so {@code RlsTenantSessionAspect} binds
 * {@code app.tenant_id} on the scan transaction. Every candidate query is
 * tenant-scoped via {@code ...AndTenantId(...)} — defence-in-depth on top of RLS.
 *
 * <h2>Idempotency / no double-dispatch</h2>
 * The scan and the dispatch are deliberately split:
 * <ol>
 *   <li>{@link #collectDueExports(UUID, OffsetDateTime)} (and the import/report
 *       analogues) run in their OWN read transaction, return only the due ids,
 *       and emit ONE {@code *_RETRY_DISPATCHED} audit per re-dispatched row
 *       (not per tick — only DUE rows are picked up).</li>
 *   <li>The {@code @Async} worker is the CLAIM POINT: it re-loads the row and
 *       transitions {@code FAILED → QUEUED → GENERATING} under its own tx +
 *       optimistic {@code @Version}. If a second tick (or the original
 *       after-commit dispatch) races, the row is no longer {@code FAILED} when
 *       the loser reads it, or the loser's optimistic write fails — so it cannot
 *       double-process. Output object keys are deterministic in the row id, so a
 *       retry overwrites its own file (no orphan partials, no duplicates).</li>
 * </ol>
 */
// Excluded from the one-shot `migrate` profile: as the only @Scheduled bean it
// would otherwise start a non-daemon scheduler thread that keeps the Liquibase
// migrator JVM alive forever, hanging the deploy. The migrator only runs DDL.
@Component
@Profile("!migrate")
public class WorkerReQueuer {

    private static final Logger log = LoggerFactory.getLogger(WorkerReQueuer.class);

    private final TenantRepository tenants;
    private final WorkerRetryScanner scanner;
    private final ExportGenerationJob exportWorker;
    private final ImportProcessingJob importWorker;
    private final ReportGenerationJob reportWorker;
    private final AuditService audit;
    private final WorkerMetrics metrics;

    public WorkerReQueuer(TenantRepository tenants,
                          WorkerRetryScanner scanner,
                          ExportGenerationJob exportWorker,
                          ImportProcessingJob importWorker,
                          ReportGenerationJob reportWorker,
                          AuditService audit,
                          WorkerMetrics metrics) {
        this.tenants = tenants;
        this.scanner = scanner;
        this.exportWorker = exportWorker;
        this.importWorker = importWorker;
        this.reportWorker = reportWorker;
        this.audit = audit;
        this.metrics = metrics;
    }

    /**
     * Periodic re-dispatch tick. {@code fixedDelayString} is externally
     * configurable; defaults to 60s with a 30s startup delay so it never fires
     * during slow integration-test context boots.
     */
    @Scheduled(
            fixedDelayString = "${grading.worker.requeue.fixed-delay-ms:60000}",
            initialDelayString = "${grading.worker.requeue.initial-delay-ms:30000}")
    public void reQueueDueFailures() {
        OffsetDateTime now = OffsetDateTime.now();
        for (TenantJpaEntity tenant : tenants.findAll()) {
            if (tenant.getStatus() != TenantStatus.ACTIVE) {
                continue;
            }
            try {
                reQueueForTenant(tenant.getId(), now);
            } catch (RuntimeException e) {
                // One tenant's failure must not abort the whole sweep.
                log.warn("WorkerReQueuer: re-queue sweep failed for tenant {}: {}",
                        tenant.getId(), e.getClass().getSimpleName());
            }
        }
    }

    /**
     * Re-queue all three worker types for ONE tenant. Each scan runs in its own
     * read transaction (so the RLS aspect binds the GUC); dispatch happens AFTER
     * the scan tx so the {@code @Async} worker (its own tx) sees a committed row.
     */
    public void reQueueForTenant(UUID tenantId, OffsetDateTime now) {
        TenantContext previous = TenantContextHolder.get();
        TenantContextHolder.set(systemContext(tenantId));
        try {
            // Scans run in their own (proxied) REQUIRES_NEW tx so the RLS aspect
            // binds app.tenant_id; dispatch happens AFTER, outside that tx.
            for (DueRow row : scanner.dueExports(tenantId, now)) {
                audit.record(retryAudit(tenantId, row, AuditAction.EXPORT_RETRY_DISPATCHED, "ExportJob"));
                metrics.retryDispatched(WorkerMetrics.JobType.EXPORT);
                exportWorker.generate(row.id(), tenantId);
            }
            for (DueRow row : scanner.dueImports(tenantId, now)) {
                audit.record(retryAudit(tenantId, row, AuditAction.IMPORT_RETRY_DISPATCHED, "ImportBatch"));
                metrics.retryDispatched(WorkerMetrics.JobType.IMPORT);
                importWorker.process(row.id(), tenantId);
            }
            for (DueRow row : scanner.dueReports(tenantId, now)) {
                audit.record(retryAudit(tenantId, row, AuditAction.REPORT_RETRY_DISPATCHED, "Report"));
                metrics.retryDispatched(WorkerMetrics.JobType.REPORT);
                reportWorker.generate(row.id(), tenantId);
            }
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
    }

    private AuditEvent retryAudit(UUID tenantId, DueRow row, String action, String entityType) {
        return AuditEvent.builder()
                .tenantId(tenantId).projectId(row.projectId())
                .action(action).entityType(entityType).entityId(row.id())
                .reason("re-dispatch attempt=" + (row.attemptCount() + 1))
                .build();
    }

    /**
     * Minimal system context for RLS binding only. Carries NO permissions — the
     * re-queuer never performs permission-gated business reads, only the worker
     * tables' own retry scan. The empty sets keep ABAC closed by construction.
     */
    private static TenantContext systemContext(UUID tenantId) {
        return new TenantContext(
                null, tenantId, Set.of(), Set.of(), Set.of(), Set.of(), false, null);
    }

    /** Lightweight carrier so dispatch happens OUTSIDE the scan transaction. */
    public record DueRow(UUID id, UUID projectId, int attemptCount) { }
}
