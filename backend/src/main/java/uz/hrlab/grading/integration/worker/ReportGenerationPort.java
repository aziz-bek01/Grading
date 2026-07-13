package uz.hrlab.grading.integration.worker;

import java.util.UUID;

/**
 * Integration-owned inbound port the {@link WorkerReQueuer} uses to re-dispatch a
 * retryable report job, WITHOUT the worker (a lower shared-kernel component)
 * depending on the reporting module's concrete {@code ReportGenerationJob}.
 *
 * <p>Dependency-inversion only — the implementation ({@code
 * reporting.infrastructure.ReportGenerationJobDispatcher}) delegates to the
 * unchanged {@code @Async} {@code ReportGenerationJob.generate(...)}, so async
 * dispatch, retry bookkeeping and generation semantics are byte-for-byte
 * identical. This mirrors how the same-module {@code ExportGenerationJob} /
 * {@code ImportProcessingJob} are already re-dispatched by concrete type; only
 * the cross-module report worker needs the port to keep the arrow pointing
 * reporting → integration.
 */
public interface ReportGenerationPort {

    /** Re-dispatch report {@code reportId} for {@code tenantId} (async, idempotent claim point). */
    void generate(UUID reportId, UUID tenantId);
}
