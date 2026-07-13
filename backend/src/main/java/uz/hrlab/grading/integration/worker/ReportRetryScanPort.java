package uz.hrlab.grading.integration.worker;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Integration-owned outbound port the {@link WorkerRetryScanner} uses to find
 * retry-due report rows, WITHOUT the scanner depending on the reporting module's
 * {@code ReportRepository} / {@code ReportJpaEntity}.
 *
 * <p>Returns the neutral, integration-owned {@link WorkerReQueuer.DueRow}
 * carriers so the reporting-side implementation ({@code
 * reporting.infrastructure.ReportRetryScanAdapter}) performs the entity → DueRow
 * projection. The scan still runs inside the scanner's own {@code REQUIRES_NEW}
 * read-only transaction (the adapter is NOT transactional), so the RLS
 * GUC-binding and the {@code tenant_id} predicate are unchanged — the query,
 * transaction boundary and result are byte-for-byte identical to the previous
 * inline {@code ReportRepository.findRetryDue(...)} call.
 */
public interface ReportRetryScanPort {

    /** Retryable, DUE, under-bound report rows for {@code tenantId} as neutral DueRow carriers. */
    List<WorkerReQueuer.DueRow> findRetryDue(UUID tenantId, OffsetDateTime now, int maxAttempts);
}
