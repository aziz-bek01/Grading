package uz.hrlab.grading.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tenant-agnostic Micrometer instrumentation for the async worker pipelines
 * (export / import / report) — Batch 6 observability.
 *
 * <h2>What it measures</h2>
 * Worker outcome counters at the REAL transition points in
 * {@code ExportGenerationJob}, {@code ImportProcessingJob},
 * {@code ReportGenerationJob} and {@code WorkerReQueuer}:
 * STARTED, SUCCEEDED, FAILED (retryable), DEAD_LETTER, RETRY_DISPATCHED — plus a
 * dead-letter gauge so an alert can fire when dead-letter volume {@code > 0}, and
 * a per-job-type Timer around generation duration.
 *
 * <h2>Cardinality + privacy (BLOCKING gates)</h2>
 * Meters are tagged by {@code type} ({@code export|import|report}) and
 * {@code outcome} ONLY. We NEVER tag by {@code tenant_id}, {@code user_id},
 * {@code project_id}, job id, file name, or any other high-cardinality /
 * identifying value, and NEVER put salary values or file contents in a metric
 * name/tag/value. This keeps the time-series count bounded
 * (job-types × outcomes) and the tenant-isolation + salary gates green.
 *
 * <h2>Lifecycle / migrate profile</h2>
 * This bean registers meters eagerly against the injected {@link MeterRegistry}
 * but starts NO threads and schedules nothing (the dead-letter gauge is a passive
 * {@link AtomicLong} polled by the registry). It is therefore safe under the
 * one-shot {@code migrate} profile — nothing here keeps the migrator JVM alive.
 */
@Component
public class WorkerMetrics {

    /** Stable metric name roots — one family per concern, disambiguated by tags. */
    static final String OUTCOME_COUNTER = "grading_worker_outcome_total";
    static final String DEAD_LETTER_GAUGE = "grading_worker_dead_letter_current";
    static final String GENERATION_TIMER = "grading_worker_generation_seconds";

    /** Job-type tag values (low cardinality, no identifiers). */
    public enum JobType {
        EXPORT("export"),
        IMPORT("import"),
        REPORT("report");

        private final String tag;

        JobType(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    /** Worker outcome tag values (low cardinality, no identifiers). */
    public enum Outcome {
        STARTED("started"),
        SUCCEEDED("succeeded"),
        FAILED("failed"),
        DEAD_LETTER("dead_letter"),
        RETRY_DISPATCHED("retry_dispatched");

        private final String tag;

        Outcome(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    private final MeterRegistry registry;

    /**
     * Process-local running count of jobs that have landed in DEAD_LETTER since
     * boot. Exposed as a gauge so "DEAD_LETTER > 0" alerts can fire even before
     * the next outcome counter increment. This is intentionally an aggregate
     * across all job-types/tenants — it carries NO tenant dimension, so it cannot
     * leak per-tenant volume. The dead-letter *counter* (tagged by type) remains
     * the authoritative rate source; this gauge is the cheap level signal.
     */
    private final AtomicLong deadLetterLevel = new AtomicLong(0);

    public WorkerMetrics(MeterRegistry registry) {
        this.registry = registry;
        // Register the dead-letter level gauge once, up-front, so it exists at
        // scrape time even when no job has dead-lettered yet (value 0).
        registry.gauge(DEAD_LETTER_GAUGE, deadLetterLevel, AtomicLong::get);
    }

    /** A worker began processing a job (attempt counted, generation about to run). */
    public void started(JobType type) {
        increment(type, Outcome.STARTED);
    }

    /** A worker finished a job successfully (terminal GENERATED / READY state). */
    public void succeeded(JobType type) {
        increment(type, Outcome.SUCCEEDED);
    }

    /** A worker hit a retryable failure (rests in FAILED with a backoff). */
    public void failed(JobType type) {
        increment(type, Outcome.FAILED);
    }

    /**
     * A job exhausted its retry budget and landed in the terminal DEAD_LETTER
     * state. Bumps both the (tagged) outcome counter AND the dead-letter level
     * gauge so an alert can trigger on {@code grading_worker_dead_letter_current > 0}.
     */
    public void deadLetter(JobType type) {
        increment(type, Outcome.DEAD_LETTER);
        deadLetterLevel.incrementAndGet();
    }

    /** The scheduled re-queuer re-dispatched a due retryable row. */
    public void retryDispatched(JobType type) {
        increment(type, Outcome.RETRY_DISPATCHED);
    }

    /**
     * Times the generation body of a worker. The caller wraps its
     * generate/process body in {@code metrics.startTimer()} / {@code stop(...)};
     * the Timer is tagged by job type only.
     */
    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    /** Stop a previously-started generation Timer sample for the given job type. */
    public void stopTimer(Timer.Sample sample, JobType type) {
        if (sample == null) {
            return;
        }
        sample.stop(Timer.builder(GENERATION_TIMER)
                .description("Async worker generation/processing duration")
                .tag("type", type.tag())
                .publishPercentileHistogram()
                .register(registry));
    }

    private void increment(JobType type, Outcome outcome) {
        Counter.builder(OUTCOME_COUNTER)
                .description("Async worker outcome events by job type and outcome")
                .tags(Tags.of("type", type.tag(), "outcome", outcome.tag()))
                .register(registry)
                .increment();
    }

    // -- escape hatches used by tests to read current values deterministically --

    /** Test-support: current dead-letter level gauge value. */
    long currentDeadLetterLevel() {
        return deadLetterLevel.get();
    }
}
