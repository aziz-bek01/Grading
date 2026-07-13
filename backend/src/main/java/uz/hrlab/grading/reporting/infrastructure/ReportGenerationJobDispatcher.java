package uz.hrlab.grading.reporting.infrastructure;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.integration.worker.ReportGenerationPort;

import java.util.UUID;

/**
 * Reporting-side implementation of the integration-owned {@link
 * ReportGenerationPort}. Delegates verbatim to the unchanged {@code @Async}
 * {@link ReportGenerationJob}, so the re-queuer's re-dispatch behaviour (async
 * hand-off on {@code reportWorkerExecutor}, retry bookkeeping, idempotent claim)
 * is identical — this class only inverts the compile-time arrow so the worker
 * depends on integration's own port instead of a reporting class.
 */
@Component
class ReportGenerationJobDispatcher implements ReportGenerationPort {

    private final ReportGenerationJob job;

    ReportGenerationJobDispatcher(ReportGenerationJob job) {
        this.job = job;
    }

    @Override
    public void generate(UUID reportId, UUID tenantId) {
        job.generate(reportId, tenantId);
    }
}
