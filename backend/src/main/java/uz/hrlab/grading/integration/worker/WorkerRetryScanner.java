package uz.hrlab.grading.integration.worker;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.integration.exports.infrastructure.ExportJobRepository;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchRepository;
import uz.hrlab.grading.reporting.infrastructure.ReportRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped retry-candidate scans for the {@link WorkerReQueuer} (Batch-4).
 *
 * <p>Extracted into its own bean ON PURPOSE so each {@code @Transactional} scan
 * is invoked through the Spring proxy (a self-invocation from inside
 * {@code WorkerReQueuer} would NOT open a transaction, leaving the RLS aspect
 * with no transaction-bound connection to {@code SET LOCAL app.tenant_id} on).
 * Each scan opens its OWN ({@code REQUIRES_NEW}, read-only) transaction so the
 * {@code RlsTenantSessionAspect} binds the GUC and forced RLS applies — the
 * re-queuer sets a system {@code TenantContext} for the iterated tenant before
 * calling.
 *
 * <p>Returns lightweight {@link WorkerReQueuer.DueRow} carriers so the actual
 * {@code @Async} dispatch happens OUTSIDE these scan transactions.
 */
@Component
public class WorkerRetryScanner {

    private final ExportJobRepository exportJobs;
    private final ImportBatchRepository importBatches;
    private final ReportRepository reports;

    public WorkerRetryScanner(ExportJobRepository exportJobs,
                              ImportBatchRepository importBatches,
                              ReportRepository reports) {
        this.exportJobs = exportJobs;
        this.importBatches = importBatches;
        this.reports = reports;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<WorkerReQueuer.DueRow> dueExports(UUID tenantId, OffsetDateTime now) {
        return exportJobs.findRetryDue(tenantId, now, WorkerRetryPolicy.MAX_ATTEMPTS).stream()
                .map(j -> new WorkerReQueuer.DueRow(j.getId(), j.getProjectId(), j.getAttemptCount()))
                .toList();
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<WorkerReQueuer.DueRow> dueImports(UUID tenantId, OffsetDateTime now) {
        return importBatches.findRetryDue(tenantId, now, WorkerRetryPolicy.MAX_ATTEMPTS).stream()
                .map(b -> new WorkerReQueuer.DueRow(b.getId(), b.getProjectId(), b.getAttemptCount()))
                .toList();
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<WorkerReQueuer.DueRow> dueReports(UUID tenantId, OffsetDateTime now) {
        return reports.findRetryDue(tenantId, now, WorkerRetryPolicy.MAX_ATTEMPTS).stream()
                .map(r -> new WorkerReQueuer.DueRow(r.getId(), r.getProjectId(), r.getAttemptCount()))
                .toList();
    }
}
