package uz.hrlab.grading.integration.worker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.metrics.WorkerMetrics;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Focused unit test for {@link WorkerReQueuer}'s report re-dispatch branch:
 * a DUE report row must be handed to the injected {@link ReportGenerationPort}
 * (dependency-inversion boundary), with its {@code REPORT_RETRY_DISPATCHED}
 * audit row and retry metric, while the export/import workers stay untouched.
 *
 * <p>The re-dispatch behaviour is scanner-driven, so the scanner is mocked to
 * return exactly one due report and no due export/import — no DB is needed.
 */
@ExtendWith(MockitoExtension.class)
class WorkerReQueuerTest {

    @Mock TenantRepository tenants;
    @Mock WorkerRetryScanner scanner;
    @Mock ExportGenerationJob exportWorker;
    @Mock ImportProcessingJob importWorker;
    @Mock ReportGenerationPort reportWorker;
    @Mock AuditService audit;
    @Mock WorkerMetrics metrics;

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    private WorkerReQueuer newReQueuer() {
        return new WorkerReQueuer(tenants, scanner, exportWorker, importWorker,
                reportWorker, audit, metrics);
    }

    @Test
    void reDispatchesDueReportThroughReportGenerationPort() {
        WorkerReQueuer reQueuer = newReQueuer();
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        given(scanner.dueExports(eq(tenantId), any())).willReturn(List.of());
        given(scanner.dueImports(eq(tenantId), any())).willReturn(List.of());
        given(scanner.dueReports(eq(tenantId), any()))
                .willReturn(List.of(new WorkerReQueuer.DueRow(reportId, projectId, 0)));

        reQueuer.reQueueForTenant(tenantId, now);

        // The one due report is re-dispatched via the port (never a concrete
        // reporting class) with the tenant sourced from the sweep, not the row.
        verify(reportWorker).generate(reportId, tenantId);
        // Its own metric + audit fire; the export/import workers stay idle.
        verify(metrics).retryDispatched(WorkerMetrics.JobType.REPORT);
        verify(exportWorker, never()).generate(any(), any());
        verify(importWorker, never()).process(any(), any());

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AuditAction.REPORT_RETRY_DISPATCHED);
        assertThat(event.getValue().entityType()).isEqualTo("Report");
        assertThat(event.getValue().entityId()).isEqualTo(reportId);
        assertThat(event.getValue().tenantId()).isEqualTo(tenantId);
        assertThat(event.getValue().projectId()).isEqualTo(projectId);
    }
}
