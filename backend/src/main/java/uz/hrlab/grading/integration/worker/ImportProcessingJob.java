package uz.hrlab.grading.integration.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.Timer;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.metrics.WorkerMetrics;
import uz.hrlab.grading.integration.excel.ExcelParser;
import uz.hrlab.grading.integration.imports.application.ImportTemplateDefinition;
import uz.hrlab.grading.integration.imports.application.ImportTemplateRegistry;
import uz.hrlab.grading.integration.imports.domain.ImportBatchStatus;
import uz.hrlab.grading.integration.imports.domain.ImportBatchStatusTransitionPolicy;
import uz.hrlab.grading.integration.imports.domain.ImportErrorLevel;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchJpaEntity;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchRepository;
import uz.hrlab.grading.integration.imports.infrastructure.ImportErrorJpaEntity;
import uz.hrlab.grading.integration.imports.infrastructure.ImportErrorRepository;
import uz.hrlab.grading.integration.storage.ObjectStorageAdapter;
import uz.hrlab.grading.integration.validation.ImportValidator;
import uz.hrlab.grading.integration.validation.ValidationError;
import uz.hrlab.grading.integration.validation.ValidationResult;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Asynchronous import processing pipeline
 * (integration-blueprint §4.2 worker contract).
 *
 * <p>Worker contract:
 * <ol>
 *   <li>Receives ONLY the batch id (no raw data in queue payload).</li>
 *   <li>Reloads the batch from the DB to recover tenant/project context.</li>
 *   <li>Runs SCAN -> PARSE -> VALIDATE pipeline; lands in READY_FOR_REVIEW or
 *       VALIDATION_FAILED.</li>
 *   <li>Each transition asserted via {@link ImportBatchStatusTransitionPolicy}.</li>
 *   <li>Audit emits IMPORT_SCAN_STARTED / IMPORT_PARSED /
 *       IMPORT_VALIDATED / IMPORT_VALIDATION_FAILED.</li>
 * </ol>
 */
@Component
public class ImportProcessingJob {

    private static final Logger log = LoggerFactory.getLogger(ImportProcessingJob.class);

    private final ImportBatchRepository batches;
    private final ImportErrorRepository errors;
    private final ImportTemplateRegistry templates;
    private final ObjectStorageAdapter storage;
    private final ExcelParser parser;
    private final ImportValidator validator;
    private final AuditService audit;
    private final WorkerMetrics metrics;

    public ImportProcessingJob(ImportBatchRepository batches,
                               ImportErrorRepository errors,
                               ImportTemplateRegistry templates,
                               ObjectStorageAdapter storage,
                               ExcelParser parser,
                               ImportValidator validator,
                               AuditService audit,
                               WorkerMetrics metrics) {
        this.batches = batches;
        this.errors = errors;
        this.templates = templates;
        this.storage = storage;
        this.parser = parser;
        this.validator = validator;
        this.audit = audit;
        this.metrics = metrics;
    }

    @Async("importWorkerExecutor")
    @Transactional
    public void process(UUID importBatchId, UUID tenantId) {
        // Worker payload contains only IDs — reload from DB with tenant context.
        if (tenantId == null) {
            log.warn("ImportProcessingJob: missing tenant context, dropping job {}", importBatchId);
            return;
        }
        ImportBatchJpaEntity batch = batches.findByIdAndTenantId(importBatchId, tenantId).orElse(null);
        if (batch == null) {
            log.warn("ImportProcessingJob: batch {} not found for tenant {}", importBatchId, tenantId);
            return;
        }
        // Batch-4 idempotency claim guard: a terminal or budget-exhausted batch
        // must NOT be re-processed even if a stale dispatch arrives. Only a fresh
        // UPLOADED or a retryable FAILED (under the bound) may proceed.
        ImportBatchStatus entryStatus = batch.getStatus();
        boolean isRetry = entryStatus == ImportBatchStatus.FAILED;
        if (entryStatus == ImportBatchStatus.DEAD_LETTER
                || (entryStatus != ImportBatchStatus.UPLOADED && !isRetry)
                || !WorkerRetryPolicy.canRetry(batch.getAttemptCount())) {
            log.warn("ImportProcessingJob: batch {} not processable (status={}, attempts={}), skipping",
                    importBatchId, entryStatus, batch.getAttemptCount());
            return;
        }
        UUID projectId = batch.getProjectId();
        String templateCode = batch.getTemplateCode();
        String storageKey = batch.getFileStorageKey();
        String traceId = batch.getTraceId();

        // Count this attempt up-front (mirrors the export/report workers).
        batch.incrementAttempt();

        // Observability (Batch 6): STARTED counter + generation Timer (type tag
        // only). The Timer is stopped in EVERY terminal path (success, transient
        // failure, dead-letter) via metrics.stopTimer.
        metrics.started(WorkerMetrics.JobType.IMPORT);
        Timer.Sample timer = metrics.startTimer();

        ImportTemplateDefinition def = templates.find(templateCode).orElse(null);
        if (def == null) {
            // Deterministic config error — retrying cannot help. Dead-letter now.
            recordError(tenantId, batch.getId(), "TEMPLATE_NOT_FOUND",
                    "Unknown template code: " + templateCode, traceId);
            deadLetter(batch, tenantId, projectId, isRetry, "TEMPLATE_NOT_FOUND");
            metrics.stopTimer(timer, WorkerMetrics.JobType.IMPORT);
            return;
        }

        // SCANNING (malware scan is a stub — call point exists for MVP 2 Phase 3 ClamAV).
        // From a fresh UPLOADED or a retryable FAILED (both -> SCANNING are allowed).
        transition(batch, ImportBatchStatus.SCANNING);
        audit.record(buildAudit(tenantId, projectId, AuditAction.IMPORT_SCAN_STARTED, batch.getId()));

        byte[] bytes;
        try {
            bytes = storage.retrieve(storageKey);
        } catch (RuntimeException e) {
            // Object-store retrieval is TRANSIENT (network/availability) — retryable.
            handleTransientFailure(batch, tenantId, projectId, "STORAGE_RETRIEVE_ERROR", e);
            metrics.stopTimer(timer, WorkerMetrics.JobType.IMPORT);
            return;
        }

        // PARSING
        transition(batch, ImportBatchStatus.PARSING);
        ExcelParser.ParsedSheet sheet;
        try {
            sheet = parser.parse(bytes);
        } catch (RuntimeException e) {
            handleTransientFailure(batch, tenantId, projectId, "PARSE_ERROR", e);
            metrics.stopTimer(timer, WorkerMetrics.JobType.IMPORT);
            return;
        }
        batch.setTotalRowCount(sheet.rows().size());
        audit.record(buildAudit(tenantId, projectId, AuditAction.IMPORT_PARSED, batch.getId()));

        // VALIDATING — levels 2, 3, 4 (5 runs at commit time)
        transition(batch, ImportBatchStatus.VALIDATING);
        ValidationResult validation = new ValidationResult();
        merge(validation, validator.validateStructure(sheet, def.requiredColumns()));
        merge(validation, validator.validateRows(sheet, def.requiredFields()));
        // Business validation hooked via callback (none defaulted at MVP 2 Phase 2)
        merge(validation, validator.validateSecurity(sheet, /*hasPerm*/ true, def.userInputFields()));

        // Persist findings
        for (ValidationError e : validation.findings()) {
            ImportErrorJpaEntity row = new ImportErrorJpaEntity(
                    UUID.randomUUID(), tenantId, batch.getId(), null,
                    e.level(), e.code(), e.fieldName(), e.message(),
                    e.suggestedFix(), e.technicalDetails(), traceId);
            errors.save(row);
        }
        batch.setErrorRowCount((int) validation.countByLevel(ImportErrorLevel.ERROR));
        batch.setWarningRowCount((int) validation.countByLevel(ImportErrorLevel.WARNING));

        // Pipeline ran to completion — clear retry bookkeeping so the re-queuer
        // never re-selects this batch (both outcomes are deterministic).
        batch.setNextAttemptAt(null);
        batch.setFailureReason(null);
        if (validation.hasBlockers()) {
            transition(batch, ImportBatchStatus.VALIDATION_FAILED);
            audit.record(buildAudit(tenantId, projectId, AuditAction.IMPORT_VALIDATION_FAILED, batch.getId()));
        } else {
            transition(batch, ImportBatchStatus.READY_FOR_REVIEW);
            audit.record(buildAudit(tenantId, projectId, AuditAction.IMPORT_VALIDATED, batch.getId()));
        }
        // The worker ran to completion without a transient/dead-letter failure.
        // VALIDATION_FAILED is a DATA outcome (blocking rows), not a worker
        // failure, so both terminal pipeline states count as a SUCCEEDED run.
        metrics.succeeded(WorkerMetrics.JobType.IMPORT);
        metrics.stopTimer(timer, WorkerMetrics.JobType.IMPORT);
        batches.save(batch);
    }

    /**
     * Bounded-retry decision for a TRANSIENT processing failure (Batch-4).
     * attempt_count was already incremented on entry. Under the bound → retryable
     * FAILED + exponential backoff; at/over the bound → terminal DEAD_LETTER.
     * Exactly ONE IMPORT_FAILED audit per attempt; dead-letter emits an
     * additional terminal event. The batch may be mid-pipeline (SCANNING/PARSING)
     * — both transition to FAILED legally.
     */
    private void handleTransientFailure(ImportBatchJpaEntity batch, UUID tenantId, UUID projectId,
                                        String code, RuntimeException e) {
        recordError(tenantId, batch.getId(), code, code + ": " + safeMessage(e), batch.getTraceId());
        batch.setFailureReason(code + ": " + safeMessage(e));
        audit.record(buildAudit(tenantId, projectId, AuditAction.IMPORT_FAILED, batch.getId()));

        if (WorkerRetryPolicy.canRetry(batch.getAttemptCount())) {
            batch.setNextAttemptAt(WorkerRetryPolicy.nextAttemptAt(OffsetDateTime.now(), batch.getAttemptCount()));
            transition(batch, ImportBatchStatus.FAILED);
            metrics.failed(WorkerMetrics.JobType.IMPORT);
            log.warn("ImportProcessingJob: batch {} failed attempt {} ({}), retry due at {}",
                    batch.getId(), batch.getAttemptCount(), code, batch.getNextAttemptAt());
        } else {
            batch.setNextAttemptAt(null);
            transition(batch, ImportBatchStatus.FAILED);
            transition(batch, ImportBatchStatus.DEAD_LETTER);
            metrics.deadLetter(WorkerMetrics.JobType.IMPORT);
            audit.record(buildAudit(tenantId, projectId, AuditAction.IMPORT_DEAD_LETTER, batch.getId()));
            log.error("ImportProcessingJob: batch {} DEAD_LETTER after {} attempts ({})",
                    batch.getId(), batch.getAttemptCount(), code);
        }
    }

    /**
     * Deterministic (non-transient) failure — retrying cannot help, so go terminal
     * immediately. The route depends on entry status: a fresh UPLOADED must pass
     * through FAILED first (UPLOADED -> DEAD_LETTER is not a legal edge), a retry
     * (already FAILED) goes straight to DEAD_LETTER.
     */
    private void deadLetter(ImportBatchJpaEntity batch, UUID tenantId, UUID projectId,
                            boolean isRetry, String code) {
        batch.setFailureReason(code);
        batch.setNextAttemptAt(null);
        audit.record(buildAudit(tenantId, projectId, AuditAction.IMPORT_FAILED, batch.getId()));
        if (!isRetry) {
            transition(batch, ImportBatchStatus.FAILED);
        }
        transition(batch, ImportBatchStatus.DEAD_LETTER);
        metrics.deadLetter(WorkerMetrics.JobType.IMPORT);
        audit.record(buildAudit(tenantId, projectId, AuditAction.IMPORT_DEAD_LETTER, batch.getId()));
        log.error("ImportProcessingJob: batch {} DEAD_LETTER (non-transient: {})", batch.getId(), code);
    }

    private void transition(ImportBatchJpaEntity batch, ImportBatchStatus to) {
        ImportBatchStatus from = batch.getStatus();
        ImportBatchStatusTransitionPolicy.assertAllowed(from, to);
        batch.setStatus(to);
        batches.save(batch);
    }

    private void recordError(UUID tenantId, UUID batchId, String code, String message, String traceId) {
        ImportErrorJpaEntity row = new ImportErrorJpaEntity(
                UUID.randomUUID(), tenantId, batchId, null,
                ImportErrorLevel.BLOCKER, code, null, message, null, null, traceId);
        errors.save(row);
    }

    private AuditEvent buildAudit(UUID tenantId, UUID projectId, String action, UUID entityId) {
        return AuditEvent.builder()
                .tenantId(tenantId)
                .projectId(projectId)
                .action(action)
                .entityType("ImportBatch")
                .entityId(entityId)
                .build();
    }

    private void merge(ValidationResult into, ValidationResult from) {
        for (ValidationError f : from.findings()) into.add(f);
    }

    private String safeMessage(Throwable t) {
        // Never echo file content — only the exception class name.
        return t.getClass().getSimpleName();
    }
}
