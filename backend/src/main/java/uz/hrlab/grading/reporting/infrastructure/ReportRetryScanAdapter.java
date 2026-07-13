package uz.hrlab.grading.reporting.infrastructure;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.integration.worker.ReportRetryScanPort;
import uz.hrlab.grading.integration.worker.WorkerReQueuer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reporting-side implementation of the integration-owned {@link
 * ReportRetryScanPort}. Runs the SAME tenant-scoped {@code
 * ReportRepository.findRetryDue(...)} query the {@code WorkerRetryScanner} used
 * inline and performs the {@code ReportJpaEntity → DueRow} projection here so
 * the scanner no longer references reporting types.
 *
 * <p>Intentionally NOT {@code @Transactional}: it is invoked from within the
 * scanner's own {@code REQUIRES_NEW} read-only transaction, so the query runs in
 * that transaction (RLS GUC bound, {@code tenant_id} predicate applied) exactly
 * as before — behaviour is byte-for-byte identical.
 */
@Component
class ReportRetryScanAdapter implements ReportRetryScanPort {

    private final ReportRepository reports;

    ReportRetryScanAdapter(ReportRepository reports) {
        this.reports = reports;
    }

    @Override
    public List<WorkerReQueuer.DueRow> findRetryDue(UUID tenantId, OffsetDateTime now, int maxAttempts) {
        return reports.findRetryDue(tenantId, now, maxAttempts).stream()
                .map(r -> new WorkerReQueuer.DueRow(r.getId(), r.getProjectId(), r.getAttemptCount()))
                .toList();
    }
}
