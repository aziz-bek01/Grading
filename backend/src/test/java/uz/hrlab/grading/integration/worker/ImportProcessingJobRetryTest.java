package uz.hrlab.grading.integration.worker;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.metrics.WorkerMetrics;
import uz.hrlab.grading.integration.excel.ExcelParser;
import uz.hrlab.grading.integration.imports.application.ImportTemplateDefinition;
import uz.hrlab.grading.integration.imports.application.ImportTemplateRegistry;
import uz.hrlab.grading.integration.imports.domain.ImportBatchStatus;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchJpaEntity;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchRepository;
import uz.hrlab.grading.integration.imports.infrastructure.ImportErrorRepository;
import uz.hrlab.grading.integration.storage.ObjectStorageAdapter;
import uz.hrlab.grading.integration.validation.ImportValidator;

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
import static org.mockito.Mockito.when;

/**
 * Bounded-retry + dead-letter unit tests for {@link ImportProcessingJob}
 * (Batch-4). Object-store retrieval is mocked to throw a TRANSIENT exception so
 * the batch rests in retryable FAILED until the bound, then dead-letters.
 */
@Tag("unit")
class ImportProcessingJobRetryTest {

    private ImportBatchRepository batches;
    private ImportErrorRepository errors;
    private ImportTemplateRegistry templates;
    private ObjectStorageAdapter storage;
    private ExcelParser parser;
    private ImportValidator validator;
    private AuditService audit;
    private List<String> auditActions;
    private ImportProcessingJob worker;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        batches = mock(ImportBatchRepository.class);
        errors = mock(ImportErrorRepository.class);
        templates = mock(ImportTemplateRegistry.class);
        storage = mock(ObjectStorageAdapter.class);
        parser = mock(ExcelParser.class);
        validator = mock(ImportValidator.class);
        audit = mock(AuditService.class);
        auditActions = new ArrayList<>();
        doAnswer(inv -> {
            auditActions.add(((AuditEvent) inv.getArgument(0)).action());
            return null;
        }).when(audit).record(any(AuditEvent.class));
        worker = new ImportProcessingJob(batches, errors, templates, storage, parser, validator, audit,
                new WorkerMetrics(new SimpleMeterRegistry()));

        ImportTemplateDefinition def = new ImportTemplateDefinition(
                "ORG_STRUCTURE", "Org structure",
                List.of("code"), List.of(), List.of("code"), List.of(),
                "IMPORT_ORG_STRUCTURE", "Department", false);
        when(templates.find(eq("ORG_STRUCTURE"))).thenReturn(Optional.of(def));
        // TRANSIENT failure: object store retrieval throws on every attempt.
        when(storage.retrieve(anyString()))
                .thenThrow(new RuntimeException("transient object-store outage"));
    }

    private ImportBatchJpaEntity newBatch() {
        ImportBatchJpaEntity batch = new ImportBatchJpaEntity(
                UUID.randomUUID(), tenantId, projectId, "ORG_STRUCTURE",
                ImportBatchStatus.UPLOADED, "org.xlsx", "key/org.xlsx",
                10L, "checksum", UUID.randomUUID(), OffsetDateTime.now());
        when(batches.findByIdAndTenantId(eq(batch.getId()), eq(tenantId)))
                .thenReturn(Optional.of(batch));
        return batch;
    }

    @Test
    void transientFailureIncrementsAttemptAndRestsInRetryableFailed() {
        ImportBatchJpaEntity batch = newBatch();
        OffsetDateTime before = OffsetDateTime.now();

        worker.process(batch.getId(), tenantId);

        assertThat(batch.getAttemptCount()).isEqualTo(1);
        assertThat(batch.getStatus()).isEqualTo(ImportBatchStatus.FAILED);
        assertThat(batch.getNextAttemptAt()).isNotNull().isAfter(before);
        assertThat(auditActions).contains(AuditAction.IMPORT_FAILED);
        assertThat(auditActions).doesNotContain(AuditAction.IMPORT_DEAD_LETTER);
    }

    @Test
    void reachingMaxAttemptsDeadLettersTerminally() {
        ImportBatchJpaEntity batch = newBatch();

        worker.process(batch.getId(), tenantId);   // attempt 1: UPLOADED -> FAILED
        assertThat(batch.getStatus()).isEqualTo(ImportBatchStatus.FAILED);
        worker.process(batch.getId(), tenantId);   // attempt 2: FAILED -> SCANNING -> FAILED
        assertThat(batch.getStatus()).isEqualTo(ImportBatchStatus.FAILED);
        worker.process(batch.getId(), tenantId);   // attempt 3 -> DEAD_LETTER

        assertThat(batch.getAttemptCount()).isEqualTo(WorkerRetryPolicy.MAX_ATTEMPTS);
        assertThat(batch.getStatus()).isEqualTo(ImportBatchStatus.DEAD_LETTER);
        assertThat(batch.getNextAttemptAt()).isNull();
        assertThat(auditActions.stream().filter(AuditAction.IMPORT_DEAD_LETTER::equals).count()).isEqualTo(1);
    }

    @Test
    void deadLetterRowIsNotReProcessed() {
        ImportBatchJpaEntity batch = newBatch();
        worker.process(batch.getId(), tenantId);
        worker.process(batch.getId(), tenantId);
        worker.process(batch.getId(), tenantId);   // DEAD_LETTER
        int size = auditActions.size();

        worker.process(batch.getId(), tenantId);   // stale -> no-op

        assertThat(batch.getStatus()).isEqualTo(ImportBatchStatus.DEAD_LETTER);
        assertThat(batch.getAttemptCount()).isEqualTo(WorkerRetryPolicy.MAX_ATTEMPTS);
        assertThat(auditActions).hasSize(size);
    }

    @Test
    void unknownTemplateDeadLettersImmediatelyNonTransient() {
        ImportBatchJpaEntity batch = new ImportBatchJpaEntity(
                UUID.randomUUID(), tenantId, projectId, "NOPE",
                ImportBatchStatus.UPLOADED, "x.xlsx", "key/x.xlsx",
                10L, "cs", UUID.randomUUID(), OffsetDateTime.now());
        when(batches.findByIdAndTenantId(eq(batch.getId()), eq(tenantId)))
                .thenReturn(Optional.of(batch));
        when(templates.find(eq("NOPE"))).thenReturn(Optional.empty());

        worker.process(batch.getId(), tenantId);

        // Deterministic config error -> straight to terminal, no retry scheduled.
        assertThat(batch.getStatus()).isEqualTo(ImportBatchStatus.DEAD_LETTER);
        assertThat(batch.getNextAttemptAt()).isNull();
        assertThat(auditActions).contains(AuditAction.IMPORT_DEAD_LETTER);
    }
}
