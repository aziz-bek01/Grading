package uz.hrlab.grading.integration.worker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.metrics.WorkerMetrics;
import uz.hrlab.grading.integration.exports.application.ExportContentGenerator;
import uz.hrlab.grading.integration.exports.domain.ExportFormat;
import uz.hrlab.grading.integration.exports.domain.ExportJobStatus;
import uz.hrlab.grading.integration.exports.domain.ExportType;
import uz.hrlab.grading.integration.exports.infrastructure.ExportJobJpaEntity;
import uz.hrlab.grading.integration.exports.infrastructure.ExportJobRepository;
import uz.hrlab.grading.integration.storage.ObjectStorageAdapter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bounded-retry + dead-letter unit tests for {@link ExportGenerationJob}
 * (Batch-4). Pure unit — no Spring, no DB. The content generator is mocked to
 * throw a transient exception so we exercise the failure path deterministically.
 */
@Tag("unit")
class ExportGenerationJobRetryTest {

    private ExportJobRepository jobs;
    private ObjectStorageAdapter storage;
    private ExportContentGenerator content;
    private AuditService audit;
    private List<String> auditActions;
    private ExportGenerationJob worker;
    private SimpleMeterRegistry registry;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jobs = mock(ExportJobRepository.class);
        storage = mock(ObjectStorageAdapter.class);
        content = mock(ExportContentGenerator.class);
        audit = mock(AuditService.class);
        auditActions = new ArrayList<>();
        doAnswer(inv -> {
            auditActions.add(((AuditEvent) inv.getArgument(0)).action());
            return null;
        }).when(audit).record(any(AuditEvent.class));
        registry = new SimpleMeterRegistry();
        worker = new ExportGenerationJob(jobs, storage, content, audit, new WorkerMetrics(registry));
        // Transient failure on every generate() call.
        when(content.generate(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("transient downstream outage"));
    }

    private double outcome(String outcome) {
        var c = registry.find("grading_worker_outcome_total")
                .tag("type", "export").tag("outcome", outcome).counter();
        return c == null ? 0.0 : c.count();
    }

    private double deadLetterGauge() {
        var g = registry.find("grading_worker_dead_letter_current").gauge();
        return g == null ? 0.0 : g.value();
    }

    private ExportJobJpaEntity newJob() {
        ExportJobJpaEntity job = new ExportJobJpaEntity(
                UUID.randomUUID(), tenantId, projectId,
                ExportType.POSITION_CATALOG, ExportFormat.XLSX,
                ExportJobStatus.REQUESTED, UUID.randomUUID(),
                OffsetDateTime.now(), null, false, false);
        when(jobs.findByIdAndTenantId(eq(job.getId()), eq(tenantId))).thenReturn(Optional.of(job));
        return job;
    }

    @Test
    void transientFailureIncrementsAttemptAndSchedulesRetry() {
        ExportJobJpaEntity job = newJob();
        OffsetDateTime before = OffsetDateTime.now();

        worker.generate(job.getId(), tenantId);

        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.FAILED);
        assertThat(job.getNextAttemptAt()).isNotNull().isAfter(before);
        assertThat(job.getFailureReason()).contains("IllegalStateException");
        assertThat(auditActions).contains(AuditAction.EXPORT_FAILED);
        assertThat(auditActions).doesNotContain(AuditAction.EXPORT_DEAD_LETTER);
        // Observability (Batch 6): a retryable failure bumps STARTED + FAILED but
        // NOT the dead-letter gauge.
        assertThat(outcome("started")).isEqualTo(1.0);
        assertThat(outcome("failed")).isEqualTo(1.0);
        assertThat(outcome("dead_letter")).isZero();
        assertThat(deadLetterGauge()).isZero();
    }

    @Test
    void reachingMaxAttemptsDeadLettersTerminally() {
        ExportJobJpaEntity job = newJob();

        // 3 failing attempts: 1 -> FAILED, 2 -> FAILED, 3 -> DEAD_LETTER.
        worker.generate(job.getId(), tenantId);   // attempt 1
        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.FAILED);
        worker.generate(job.getId(), tenantId);   // attempt 2
        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.FAILED);
        worker.generate(job.getId(), tenantId);   // attempt 3 -> dead-letter

        assertThat(job.getAttemptCount()).isEqualTo(WorkerRetryPolicy.MAX_ATTEMPTS);
        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.DEAD_LETTER);
        assertThat(job.getNextAttemptAt()).isNull();
        assertThat(auditActions).contains(AuditAction.EXPORT_DEAD_LETTER);
        // Exactly one failure audit per attempt (3) + one terminal dead-letter event.
        assertThat(auditActions.stream().filter(AuditAction.EXPORT_FAILED::equals).count()).isEqualTo(3);
        assertThat(auditActions.stream().filter(AuditAction.EXPORT_DEAD_LETTER::equals).count()).isEqualTo(1);
        // Observability (Batch 6): 3 STARTED, 2 retryable FAILED, 1 DEAD_LETTER,
        // and the dead-letter gauge sits at 1 so the > 0 alert fires.
        assertThat(outcome("started")).isEqualTo(3.0);
        assertThat(outcome("failed")).isEqualTo(2.0);
        assertThat(outcome("dead_letter")).isEqualTo(1.0);
        assertThat(deadLetterGauge()).isEqualTo(1.0);
    }

    @Test
    void deadLetterRowIsNotReProcessed() {
        ExportJobJpaEntity job = newJob();
        worker.generate(job.getId(), tenantId);
        worker.generate(job.getId(), tenantId);
        worker.generate(job.getId(), tenantId);   // now DEAD_LETTER
        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.DEAD_LETTER);
        int actionsAfterDeadLetter = auditActions.size();

        // A stale re-dispatch must be a no-op (idempotency claim guard).
        worker.generate(job.getId(), tenantId);

        assertThat(job.getAttemptCount()).isEqualTo(WorkerRetryPolicy.MAX_ATTEMPTS);
        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.DEAD_LETTER);
        assertThat(auditActions).hasSize(actionsAfterDeadLetter); // no new audit
        // The 4th (stale) dispatch did NOT attempt generation again — content was
        // invoked only the 3 times that actually ran (the dead row short-circuits).
        verify(content, org.mockito.Mockito.times(WorkerRetryPolicy.MAX_ATTEMPTS))
                .generate(any(), any(), any(), any(), any());
        verify(storage, never()).store(any(), anyString(), any());
    }

    @Test
    void missingTenantIsADefensiveNoOp() {
        ExportJobJpaEntity job = newJob();
        worker.generate(job.getId(), null);
        assertThat(job.getAttemptCount()).isZero();
        assertThat(auditActions).isEmpty();
        // No worker ran, so no STARTED/SUCCEEDED meter was touched.
        assertThat(outcome("started")).isZero();
        assertThat(outcome("succeeded")).isZero();
    }

    @Test
    void successfulGenerationBumpsSucceededAndTimerNotDeadLetter() {
        ExportJobJpaEntity job = newJob();
        // Override the default failing stub: this generate() succeeds. Use
        // doReturn so re-stubbing does not invoke the prior thenThrow stub.
        org.mockito.Mockito.doReturn(
                new ExportContentGenerator.GeneratedExport(
                        new byte[]{1, 2, 3}, 1, "xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .when(content).generate(any(), any(), any(), any(), any());

        worker.generate(job.getId(), tenantId);

        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.GENERATED);
        // Observability (Batch 6): a success bumps STARTED + SUCCEEDED, records a
        // generation Timer sample, and leaves the dead-letter gauge at zero.
        assertThat(outcome("started")).isEqualTo(1.0);
        assertThat(outcome("succeeded")).isEqualTo(1.0);
        assertThat(outcome("failed")).isZero();
        assertThat(outcome("dead_letter")).isZero();
        assertThat(deadLetterGauge()).isZero();
        var timer = registry.find("grading_worker_generation_seconds").tag("type", "export").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
    }
}
