package uz.hrlab.grading.integration.imports.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.integration.imports.domain.ImportBatchStatus;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchJpaEntity;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchRepository;
import uz.hrlab.grading.integration.storage.ObjectStorageAdapter;
import uz.hrlab.grading.integration.storage.ObjectStoragePath;
import uz.hrlab.grading.integration.validation.ImportValidator;
import uz.hrlab.grading.integration.validation.ValidationError;
import uz.hrlab.grading.integration.validation.ValidationResult;
import uz.hrlab.grading.integration.worker.ImportProcessingJob;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Upload an import file (multipart) — runs level-1 file validation, stores
 * the file in tenant/project namespace, creates the ImportBatch row, and
 * dispatches the async ImportProcessingJob.
 *
 * <p>Permission is template-dependent and resolved through the
 * {@link ImportTemplateRegistry}. Cross-tenant references in the body are
 * impossible because the tenantId is sourced from {@link TenantContextHolder}.
 */
@Service
public class UploadImportFileUseCase {

    private final ImportBatchRepository batches;
    private final ImportTemplateRegistry templates;
    private final ObjectStorageAdapter storage;
    private final ImportValidator validator;
    private final ImportProcessingJob processor;
    private final AuditService audit;

    @Autowired
    public UploadImportFileUseCase(ImportBatchRepository batches,
                                   ImportTemplateRegistry templates,
                                   ObjectStorageAdapter storage,
                                   ImportValidator validator,
                                   ImportProcessingJob processor,
                                   AuditService audit) {
        this.batches = batches;
        this.templates = templates;
        this.storage = storage;
        this.validator = validator;
        this.processor = processor;
        this.audit = audit;
    }

    @Transactional
    public UUID upload(MultipartFile file, String templateCode, UUID projectId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        ImportTemplateDefinition def = templates.find(templateCode)
                .orElseThrow(() -> new ValidationException("UNKNOWN_TEMPLATE_CODE: " + templateCode));
        if (!ctx.hasPermission(def.requiredPermission())) {
            throw new PermissionDeniedException();
        }
        if (file == null || file.isEmpty()) {
            throw new ValidationException("FILE_REQUIRED");
        }

        // Level-1 file validation
        ValidationResult level1 = validator.validateFile(
                file.getContentType(), file.getOriginalFilename(), file.getSize());
        if (level1.hasBlockers()) {
            String msg = level1.findings().stream().findFirst()
                    .map(ValidationError::code).orElse("FILE_VALIDATION_FAILED");
            throw new ValidationException(msg);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("FILE_READ_FAILED");
        }

        UUID batchId = UUID.randomUUID();
        String storageKey = ObjectStoragePath.forImportOriginal(ctx.tenantId(), projectId, batchId);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("tenant_id", ctx.tenantId().toString());
        metadata.put("project_id", projectId == null ? "" : projectId.toString());
        metadata.put("template_code", templateCode);
        metadata.put("uploaded_by", ctx.userId().toString());
        metadata.put("checksum_sha256", sha256(bytes));
        storage.store(bytes, storageKey, metadata);

        ImportBatchJpaEntity batch = new ImportBatchJpaEntity(
                batchId, ctx.tenantId(), projectId, templateCode,
                ImportBatchStatus.UPLOADED, file.getOriginalFilename(),
                storageKey, file.getSize(), metadata.get("checksum_sha256"),
                ctx.userId(), OffsetDateTime.now());
        batch.setContainsSalaryData(def.containsSalaryData());
        batch.setTraceId(UUID.randomUUID().toString());
        batches.save(batch);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(projectId)
                .actorUserId(ctx.userId())
                .action(AuditAction.IMPORT_UPLOADED)
                .entityType("ImportBatch")
                .entityId(batchId)
                .reason("template=" + templateCode + " size=" + file.getSize())
                .build());
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(projectId)
                .actorUserId(ctx.userId())
                .action(AuditAction.FILE_UPLOADED)
                .entityType("ImportBatch")
                .entityId(batchId)
                .build());

        // PERF/CORRECTNESS (P1) — dispatch the @Async pipeline only AFTER this
        // transaction commits. Dispatching inline raced the commit: the worker
        // (its own tx) could load the ImportBatch row before it was visible and
        // find batch == null, silently dropping the job. Deferring to afterCommit
        // guarantees the row is persisted before the worker reads it. If the tx
        // rolls back, no dispatch happens (correct — there is nothing to process).
        UUID tenantId = ctx.tenantId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    processor.process(batchId, tenantId);
                }
            });
        } else {
            // No active transaction (defensive) — dispatch immediately.
            processor.process(batchId, tenantId);
        }

        return batchId;
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
