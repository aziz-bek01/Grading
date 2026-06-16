package uz.hrlab.grading.reporting.infrastructure;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.metrics.WorkerMetrics;
import uz.hrlab.grading.integration.storage.ObjectStorageAdapter;
import uz.hrlab.grading.integration.worker.WorkerRetryPolicy;
import uz.hrlab.grading.reporting.application.template.ReportTemplateRegistry;
import uz.hrlab.grading.reporting.domain.ReportFormat;
import uz.hrlab.grading.reporting.domain.ReportStatus;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bounded-retry + dead-letter unit tests for {@link ReportGenerationJob}
 * (Batch-4). The template registry is mocked to throw a transient exception.
 */
@Tag("unit")
class ReportGenerationJobRetryTest {

    private ReportRepository reports;
    private ObjectStorageAdapter storage;
    private ReportTemplateRegistry templates;
    private AuditService audit;
    private List<String> auditActions;
    private ReportGenerationJob worker;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reports = mock(ReportRepository.class);
        storage = mock(ObjectStorageAdapter.class);
        templates = mock(ReportTemplateRegistry.class);
        audit = mock(AuditService.class);
        auditActions = new ArrayList<>();
        doAnswer(inv -> {
            auditActions.add(((AuditEvent) inv.getArgument(0)).action());
            return null;
        }).when(audit).record(any(AuditEvent.class));
        worker = new ReportGenerationJob(reports, storage, templates, audit,
                new WorkerMetrics(new SimpleMeterRegistry()));
        when(templates.require(any(), any()))
                .thenThrow(new IllegalStateException("transient template lookup outage"));
    }

    private ReportJpaEntity newReport() {
        ReportJpaEntity report = new ReportJpaEntity(
                UUID.randomUUID(), tenantId, projectId,
                ReportType.GRADE_DISTRIBUTION, ReportFormat.PDF,
                ReportStatus.REQUESTED, "Grade distribution",
                UUID.randomUUID(), OffsetDateTime.now(), null, "en-US", false, false);
        when(reports.findByIdAndTenantId(eq(report.getId()), eq(tenantId)))
                .thenReturn(Optional.of(report));
        return report;
    }

    @Test
    void transientFailureIncrementsAttemptAndSchedulesRetry() {
        ReportJpaEntity report = newReport();
        OffsetDateTime before = OffsetDateTime.now();

        worker.generate(report.getId(), tenantId);

        assertThat(report.getAttemptCount()).isEqualTo(1);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(report.getNextAttemptAt()).isNotNull().isAfter(before);
        assertThat(auditActions).contains(AuditAction.REPORT_FAILED);
        assertThat(auditActions).doesNotContain(AuditAction.REPORT_DEAD_LETTER);
    }

    @Test
    void reachingMaxAttemptsDeadLettersTerminally() {
        ReportJpaEntity report = newReport();

        worker.generate(report.getId(), tenantId);   // attempt 1
        worker.generate(report.getId(), tenantId);   // attempt 2
        worker.generate(report.getId(), tenantId);   // attempt 3 -> dead-letter

        assertThat(report.getAttemptCount()).isEqualTo(WorkerRetryPolicy.MAX_ATTEMPTS);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.DEAD_LETTER);
        assertThat(report.getNextAttemptAt()).isNull();
        assertThat(auditActions.stream().filter(AuditAction.REPORT_FAILED::equals).count()).isEqualTo(3);
        assertThat(auditActions.stream().filter(AuditAction.REPORT_DEAD_LETTER::equals).count()).isEqualTo(1);
    }

    @Test
    void deadLetterRowIsNotReProcessed() {
        ReportJpaEntity report = newReport();
        worker.generate(report.getId(), tenantId);
        worker.generate(report.getId(), tenantId);
        worker.generate(report.getId(), tenantId);   // DEAD_LETTER
        int size = auditActions.size();

        worker.generate(report.getId(), tenantId);   // stale -> no-op

        assertThat(report.getStatus()).isEqualTo(ReportStatus.DEAD_LETTER);
        assertThat(report.getAttemptCount()).isEqualTo(WorkerRetryPolicy.MAX_ATTEMPTS);
        assertThat(auditActions).hasSize(size);
    }
}
