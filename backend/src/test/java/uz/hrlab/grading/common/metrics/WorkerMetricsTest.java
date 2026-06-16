package uz.hrlab.grading.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WorkerMetrics} (Batch 6 observability). Asserts the
 * meters are registered, increment at the right outcome seams, are tagged ONLY
 * by {@code type}/{@code outcome} (never tenant/user/project), and that the
 * dead-letter gauge tracks dead-letter volume so an alert can fire on {@code > 0}.
 */
@Tag("unit")
class WorkerMetricsTest {

    private MeterRegistry registry;
    private WorkerMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new WorkerMetrics(registry);
    }

    private double outcome(String type, String outcome) {
        Counter c = registry.find(WorkerMetrics.OUTCOME_COUNTER)
                .tag("type", type).tag("outcome", outcome).counter();
        return c == null ? 0.0 : c.count();
    }

    @Test
    void deadLetterGaugeRegisteredAtZeroOnConstruction() {
        Gauge gauge = registry.find(WorkerMetrics.DEAD_LETTER_GAUGE).gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isZero();
    }

    @Test
    void succeededBumpsSucceededCounterTaggedByType() {
        metrics.succeeded(WorkerMetrics.JobType.EXPORT);

        assertThat(outcome("export", "succeeded")).isEqualTo(1.0);
        assertThat(outcome("export", "failed")).isZero();
        assertThat(outcome("import", "succeeded")).isZero();
    }

    @Test
    void failedBumpsRetryableFailedCounterButNotDeadLetterGauge() {
        metrics.failed(WorkerMetrics.JobType.IMPORT);

        assertThat(outcome("import", "failed")).isEqualTo(1.0);
        assertThat(registry.find(WorkerMetrics.DEAD_LETTER_GAUGE).gauge().value()).isZero();
    }

    @Test
    void deadLetterBumpsCounterAndGaugeSoAlertCanFire() {
        metrics.deadLetter(WorkerMetrics.JobType.REPORT);

        assertThat(outcome("report", "dead_letter")).isEqualTo(1.0);
        // The gauge moves > 0 so a Prometheus rule on
        // grading_worker_dead_letter_current > 0 fires.
        assertThat(registry.find(WorkerMetrics.DEAD_LETTER_GAUGE).gauge().value()).isEqualTo(1.0);
        assertThat(metrics.currentDeadLetterLevel()).isEqualTo(1L);

        metrics.deadLetter(WorkerMetrics.JobType.EXPORT);
        assertThat(registry.find(WorkerMetrics.DEAD_LETTER_GAUGE).gauge().value()).isEqualTo(2.0);
    }

    @Test
    void startedAndRetryDispatchedAreDistinctOutcomes() {
        metrics.started(WorkerMetrics.JobType.EXPORT);
        metrics.retryDispatched(WorkerMetrics.JobType.EXPORT);

        assertThat(outcome("export", "started")).isEqualTo(1.0);
        assertThat(outcome("export", "retry_dispatched")).isEqualTo(1.0);
    }

    @Test
    void timerRecordsAGenerationSampleTaggedByTypeOnly() {
        Timer.Sample sample = metrics.startTimer();
        metrics.stopTimer(sample, WorkerMetrics.JobType.REPORT);

        Timer timer = registry.find(WorkerMetrics.GENERATION_TIMER).tag("type", "report").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
    }

    @Test
    void stopTimerToleratesNullSample() {
        // Defensive: a worker that short-circuits before starting a timer must not NPE.
        metrics.stopTimer(null, WorkerMetrics.JobType.IMPORT);
        // No timer meter should have been created.
        assertThat(registry.find(WorkerMetrics.GENERATION_TIMER).timers()).isEmpty();
    }

    @Test
    void metersAreNeverTaggedWithIdentifyingDimensions() {
        // Drive every seam, then assert NO meter carries a forbidden tag key.
        metrics.started(WorkerMetrics.JobType.EXPORT);
        metrics.succeeded(WorkerMetrics.JobType.EXPORT);
        metrics.failed(WorkerMetrics.JobType.IMPORT);
        metrics.deadLetter(WorkerMetrics.JobType.REPORT);
        metrics.retryDispatched(WorkerMetrics.JobType.IMPORT);
        metrics.stopTimer(metrics.startTimer(), WorkerMetrics.JobType.EXPORT);

        registry.getMeters().forEach(meter ->
                meter.getId().getTags().forEach(tag -> {
                    String key = tag.getKey().toLowerCase();
                    assertThat(key)
                            .as("forbidden high-cardinality / identifying tag on meter %s",
                                    meter.getId().getName())
                            .isNotIn("tenant_id", "tenantid", "tenant",
                                    "user_id", "userid", "user",
                                    "project_id", "projectid", "project",
                                    "job_id", "jobid", "id", "salary");
                }));
    }
}
