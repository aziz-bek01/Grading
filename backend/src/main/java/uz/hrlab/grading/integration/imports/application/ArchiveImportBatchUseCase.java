package uz.hrlab.grading.integration.imports.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.integration.imports.api.ImportBatchResponse;
import uz.hrlab.grading.integration.imports.domain.ImportBatchStatus;
import uz.hrlab.grading.integration.imports.domain.ImportBatchStatusTransitionPolicy;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchJpaEntity;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * Archives a terminal import batch (COMMITTED, PARTIALLY_COMMITTED, CANCELLED,
 * FAILED, SCAN_FAILED or VALIDATION_FAILED) so it drops out of the active
 * imports list.
 *
 * <p>Archiving is a NON-DESTRUCTIVE, retention-only action — it never touches
 * already-committed rows/org-structure data. It exists specifically for
 * PARTIALLY_COMMITTED/COMMITTED batches, which {@link CancelImportBatchUseCase}
 * can no longer cancel (rows are already live); see
 * {@link ImportBatchStatusTransitionPolicy}.
 */
@Service
public class ArchiveImportBatchUseCase {

    private final ImportBatchRepository batches;
    private final AuditService audit;

    public ArchiveImportBatchUseCase(ImportBatchRepository batches, AuditService audit) {
        this.batches = batches;
        this.audit = audit;
    }

    // BE-035 — returns the wire DTO (mapped in-tx), never the JpaEntity.
    @Transactional
    public ImportBatchResponse archive(UUID batchId) {
        TenantContext ctx = TenantContextHolder.requireActive().requireAny(
                PermissionCodes.IMPORT_CANCEL, PermissionCodes.ORG_IMPORT);
        ImportBatchJpaEntity batch = batches.findByIdAndTenantId(batchId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        ImportBatchStatusTransitionPolicy.assertAllowed(batch.getStatus(), ImportBatchStatus.ARCHIVED);
        batch.setStatus(ImportBatchStatus.ARCHIVED);
        audit.record(AuditEvent.builder(ctx)
                .projectId(batch.getProjectId())
                .action(AuditAction.IMPORT_ARCHIVED)
                .entityType("ImportBatch")
                .entityId(batch.getId())
                .build());
        return ImportBatchResponse.from(batches.save(batch));
    }
}
