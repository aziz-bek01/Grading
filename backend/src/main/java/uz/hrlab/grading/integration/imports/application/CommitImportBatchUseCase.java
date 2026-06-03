package uz.hrlab.grading.integration.imports.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.integration.excel.ExcelParser;
import uz.hrlab.grading.integration.imports.domain.ImportBatchStatus;
import uz.hrlab.grading.integration.imports.domain.ImportBatchStatusTransitionPolicy;
import uz.hrlab.grading.integration.imports.domain.ImportErrorLevel;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchJpaEntity;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchRepository;
import uz.hrlab.grading.integration.imports.infrastructure.ImportErrorJpaEntity;
import uz.hrlab.grading.integration.imports.infrastructure.ImportErrorRepository;
import uz.hrlab.grading.integration.storage.ObjectStorageAdapter;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Final commit step — moves a batch from READY_FOR_REVIEW → READY_TO_COMMIT
 * → COMMITTING → COMMITTED / PARTIALLY_COMMITTED / FAILED.
 *
 * <p>PO-11: for each template that has a registered
 * {@link ImportRowCommitter} (ORG_STRUCTURE_V1, POSITION_CATALOG_V1,
 * GRADE_BANDS_V1 in MVP 2 Phase 3), every parsed row is materialized into a
 * real domain entity (Department, Position, GradeBand). Templates without a
 * committer (JOB_PROFILE_V1, METHODOLOGY_FACTORS_V1) reject the commit
 * explicitly — no silent no-op.
 *
 * <p>Failure handling per row: an {@link ImportRowCommitException} is logged
 * as an {@link ImportErrorJpaEntity}; the row is counted as failed and the
 * batch lands in PARTIALLY_COMMITTED (mixed) or FAILED (zero succeeded).
 * Other rows continue — a single bad row never aborts the whole batch.
 */
@Service
public class CommitImportBatchUseCase {

    private static final Logger log = LoggerFactory.getLogger(CommitImportBatchUseCase.class);

    private final ImportBatchRepository batches;
    private final ImportErrorRepository errors;
    private final ImportTemplateRegistry templates;
    private final ImportRowCommitterRegistry committers;
    private final ObjectStorageAdapter storage;
    private final ExcelParser parser;
    private final AbacGate abacGate;
    private final AuditService audit;

    public CommitImportBatchUseCase(ImportBatchRepository batches,
                                    ImportErrorRepository errors,
                                    ImportTemplateRegistry templates,
                                    ImportRowCommitterRegistry committers,
                                    ObjectStorageAdapter storage,
                                    ExcelParser parser,
                                    AbacGate abacGate,
                                    AuditService audit) {
        this.batches = batches;
        this.errors = errors;
        this.templates = templates;
        this.committers = committers;
        this.storage = storage;
        this.parser = parser;
        this.abacGate = abacGate;
        this.audit = audit;
    }

    @Transactional
    public ImportBatchJpaEntity commit(UUID batchId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        ImportBatchJpaEntity batch = batches.findByIdAndTenantId(batchId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        ImportTemplateDefinition def = templates.find(batch.getTemplateCode())
                .orElseThrow(() -> new ValidationException("UNKNOWN_TEMPLATE_CODE"));
        // Level-5 security re-check at commit time.
        if (!ctx.hasPermission(def.requiredPermission())) {
            throw new PermissionDeniedException();
        }
        // ABAC project membership re-check — the originating UPLOAD checked at
        // upload time but the user / role grants may have changed since.
        if (batch.getProjectId() != null) {
            abacGate.enforceCanWriteInProject(ctx, batch.getProjectId());
        }
        // Reject explicitly if no committer is registered for this template
        // — refuse to flip status to COMMITTED while nothing actually commits.
        ImportRowCommitter committer = committers.findCommitter(batch.getTemplateCode())
                .orElseThrow(() -> new ValidationException(
                        "COMMIT_NOT_SUPPORTED",
                        "Template '" + batch.getTemplateCode()
                                + "' commit is deferred to a future MVP 2 phase"));

        // Allow direct commit from READY_FOR_REVIEW (skip intermediate READY_TO_COMMIT).
        if (batch.getStatus() == ImportBatchStatus.READY_FOR_REVIEW) {
            transition(batch, ImportBatchStatus.READY_TO_COMMIT);
        }
        transition(batch, ImportBatchStatus.COMMITTING);

        // Re-parse the stored file — the original parsed payload is not
        // persisted in MVP 2 Phase 2; re-parsing keeps the worker contract
        // lean and re-runs Level-5 trust checks on the same bytes that were
        // originally validated.
        byte[] bytes;
        try {
            bytes = storage.retrieve(batch.getFileStorageKey());
        } catch (RuntimeException e) {
            transition(batch, ImportBatchStatus.FAILED);
            recordError(ctx.tenantId(), batch.getId(), "STORAGE_RETRIEVE_FAILED",
                    "Failed to retrieve uploaded file from storage", batch.getTraceId());
            audit.record(buildBatchAudit(ctx, batch, AuditAction.IMPORT_FAILED));
            return batches.save(batch);
        }
        ExcelParser.ParsedSheet sheet;
        try {
            sheet = parser.parse(bytes);
        } catch (RuntimeException e) {
            transition(batch, ImportBatchStatus.FAILED);
            recordError(ctx.tenantId(), batch.getId(), "COMMIT_PARSE_FAILED",
                    "Failed to re-parse uploaded file at commit time", batch.getTraceId());
            audit.record(buildBatchAudit(ctx, batch, AuditAction.IMPORT_FAILED));
            return batches.save(batch);
        }

        ImportRowCommitContext commitCtx = new ImportRowCommitContext(
                ctx.tenantId(), batch.getProjectId(), ctx.userId(),
                batch.getId(), batch.getTraceId());

        // Two-pass support for parent-child templates (ORG_STRUCTURE_V1):
        // pass 1 commits root rows (no parent_external_id), pass 2 commits
        // children. For templates without parent_external_id the column is
        // absent and pass 2 is a no-op for non-ORG templates because all rows
        // are already committed in pass 1.
        List<Map<String, String>> rows = sheet.rows();
        boolean[] committedFlag = new boolean[rows.size()];
        int committedCount = 0;
        int failedCount = 0;

        // Two passes — first pass: rows with no parent reference; second
        // pass: rows whose parents were committed in pass 1 OR exist already.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < rows.size(); i++) {
                if (committedFlag[i]) continue;
                Map<String, String> row = rows.get(i);
                String parentRef = row.get("parent_external_id");
                boolean isRootPass = parentRef == null || parentRef.isBlank();
                if (pass == 0 && !isRootPass) continue;
                try {
                    committer.commit(row, commitCtx);
                    committedFlag[i] = true;
                    committedCount++;
                } catch (ImportRowCommitException rowErr) {
                    if (pass == 0 && !isRootPass) {
                        // Defer to pass 2 — parent might land later in pass 1.
                        continue;
                    }
                    recordRowError(ctx.tenantId(), batch.getId(), i + 2 /* header offset */,
                            rowErr.getCode(), rowErr.getMessage(), batch.getTraceId());
                    committedFlag[i] = true; // Mark as processed even on failure.
                    failedCount++;
                } catch (RuntimeException unexpected) {
                    log.warn("Unexpected commit failure for row {} in batch {}: {}",
                            i + 2, batch.getId(), unexpected.getClass().getSimpleName());
                    recordRowError(ctx.tenantId(), batch.getId(), i + 2,
                            "UNEXPECTED_COMMIT_FAILURE",
                            "Row commit raised " + unexpected.getClass().getSimpleName(),
                            batch.getTraceId());
                    committedFlag[i] = true;
                    failedCount++;
                }
            }
        }

        batch.setCommittedRowCount(committedCount);
        // Update the error count to reflect commit-time failures (validation
        // errors already recorded by the worker remain in import_errors).
        Integer existingErrors = batch.getErrorRowCount() == null ? 0 : batch.getErrorRowCount();
        batch.setErrorRowCount(existingErrors + failedCount);

        ImportBatchStatus finalStatus;
        String finalAuditAction;
        if (committedCount == 0 && failedCount > 0) {
            finalStatus = ImportBatchStatus.FAILED;
            finalAuditAction = AuditAction.IMPORT_FAILED;
        } else if (failedCount > 0) {
            finalStatus = ImportBatchStatus.PARTIALLY_COMMITTED;
            finalAuditAction = AuditAction.IMPORT_PARTIALLY_COMMITTED;
        } else {
            finalStatus = ImportBatchStatus.COMMITTED;
            finalAuditAction = AuditAction.IMPORT_COMMITTED;
        }
        transition(batch, finalStatus);
        batch.setCommittedBy(ctx.userId());
        batch.setCommittedAt(OffsetDateTime.now());
        ImportBatchJpaEntity saved = batches.save(batch);
        audit.record(buildBatchAudit(ctx, saved, finalAuditAction));
        return saved;
    }

    private void transition(ImportBatchJpaEntity batch, ImportBatchStatus to) {
        ImportBatchStatusTransitionPolicy.assertAllowed(batch.getStatus(), to);
        batch.setStatus(to);
    }

    private AuditEvent buildBatchAudit(TenantContext ctx, ImportBatchJpaEntity batch, String action) {
        return AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(batch.getProjectId())
                .actorUserId(ctx.userId())
                .action(action)
                .entityType("ImportBatch")
                .entityId(batch.getId())
                .reason("template=" + batch.getTemplateCode())
                .build();
    }

    private void recordError(UUID tenantId, UUID batchId, String code, String message, String traceId) {
        errors.save(new ImportErrorJpaEntity(
                UUID.randomUUID(), tenantId, batchId, null,
                ImportErrorLevel.BLOCKER, code, null, message, null, null, traceId));
    }

    private void recordRowError(UUID tenantId, UUID batchId, int rowNumber,
                                String code, String message, String traceId) {
        errors.save(new ImportErrorJpaEntity(
                UUID.randomUUID(), tenantId, batchId, null,
                ImportErrorLevel.ERROR, code, null,
                "Row " + rowNumber + ": " + message, null, null, traceId));
    }
}
