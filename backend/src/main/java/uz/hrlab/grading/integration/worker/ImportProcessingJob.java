package uz.hrlab.grading.integration.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
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

    public ImportProcessingJob(ImportBatchRepository batches,
                               ImportErrorRepository errors,
                               ImportTemplateRegistry templates,
                               ObjectStorageAdapter storage,
                               ExcelParser parser,
                               ImportValidator validator,
                               AuditService audit) {
        this.batches = batches;
        this.errors = errors;
        this.templates = templates;
        this.storage = storage;
        this.parser = parser;
        this.validator = validator;
        this.audit = audit;
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
        UUID projectId = batch.getProjectId();
        String templateCode = batch.getTemplateCode();
        String storageKey = batch.getFileStorageKey();
        String traceId = batch.getTraceId();

        ImportTemplateDefinition def = templates.find(templateCode).orElse(null);
        if (def == null) {
            transition(batch, ImportBatchStatus.FAILED);
            recordError(tenantId, batch.getId(), "TEMPLATE_NOT_FOUND",
                    "Unknown template code: " + templateCode, traceId);
            audit.record(buildAudit(tenantId, projectId, AuditAction.IMPORT_FAILED, batch.getId()));
            return;
        }

        // SCANNING (malware scan is a stub — call point exists for MVP 2 Phase 3 ClamAV)
        transition(batch, ImportBatchStatus.SCANNING);
        audit.record(buildAudit(tenantId, projectId, AuditAction.IMPORT_SCAN_STARTED, batch.getId()));

        byte[] bytes;
        try {
            bytes = storage.retrieve(storageKey);
        } catch (RuntimeException e) {
            transition(batch, ImportBatchStatus.SCAN_FAILED);
            audit.record(buildAudit(tenantId, projectId, AuditAction.IMPORT_SCAN_FAILED, batch.getId()));
            return;
        }

        // PARSING
        transition(batch, ImportBatchStatus.PARSING);
        ExcelParser.ParsedSheet sheet;
        try {
            sheet = parser.parse(bytes);
        } catch (RuntimeException e) {
            transition(batch, ImportBatchStatus.FAILED);
            recordError(tenantId, batch.getId(), "PARSE_ERROR",
                    "Failed to parse workbook: " + safeMessage(e), traceId);
            audit.record(buildAudit(tenantId, projectId, AuditAction.IMPORT_FAILED, batch.getId()));
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

        if (validation.hasBlockers()) {
            transition(batch, ImportBatchStatus.VALIDATION_FAILED);
            audit.record(buildAudit(tenantId, projectId, AuditAction.IMPORT_VALIDATION_FAILED, batch.getId()));
        } else {
            transition(batch, ImportBatchStatus.READY_FOR_REVIEW);
            audit.record(buildAudit(tenantId, projectId, AuditAction.IMPORT_VALIDATED, batch.getId()));
        }
        batches.save(batch);
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
