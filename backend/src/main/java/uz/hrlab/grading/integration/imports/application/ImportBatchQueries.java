package uz.hrlab.grading.integration.imports.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.integration.imports.domain.ImportBatchStatus;
import uz.hrlab.grading.integration.imports.domain.ImportErrorLevel;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchJpaEntity;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchRepository;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchRowJpaEntity;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchRowRepository;
import uz.hrlab.grading.integration.imports.infrastructure.ImportErrorJpaEntity;
import uz.hrlab.grading.integration.imports.infrastructure.ImportErrorRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/** Read-side queries for import batches, rows, and errors. */
@Service
public class ImportBatchQueries {

    private final ImportBatchRepository batches;
    private final ImportBatchRowRepository rows;
    private final ImportErrorRepository errors;

    public ImportBatchQueries(ImportBatchRepository batches,
                              ImportBatchRowRepository rows,
                              ImportErrorRepository errors) {
        this.batches = batches;
        this.rows = rows;
        this.errors = errors;
    }

    @Transactional(readOnly = true)
    public Page<ImportBatchJpaEntity> list(UUID projectId, ImportBatchStatus status, Pageable pageable) {
        TenantContext ctx = requireRead();
        if (status == null) {
            return batches.findAllByTenantIdAndProjectId(ctx.tenantId(), projectId, pageable);
        }
        return batches.findAllByTenantIdAndProjectIdAndStatus(ctx.tenantId(), projectId, status, pageable);
    }

    @Transactional(readOnly = true)
    public ImportBatchJpaEntity get(UUID batchId) {
        TenantContext ctx = requireRead();
        return batches.findByIdAndTenantId(batchId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
    }

    @Transactional(readOnly = true)
    public Page<ImportBatchRowJpaEntity> listRows(UUID batchId, Pageable pageable) {
        TenantContext ctx = requireRead();
        // ownership probe — same 404 pattern as evaluation
        batches.findByIdAndTenantId(batchId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        return rows.findAllByTenantIdAndImportBatchId(ctx.tenantId(), batchId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ImportErrorJpaEntity> listErrors(UUID batchId, ImportErrorLevel level, Pageable pageable) {
        TenantContext ctx = requireRead();
        batches.findByIdAndTenantId(batchId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (level == null) {
            return errors.findAllByTenantIdAndImportBatchId(ctx.tenantId(), batchId, pageable);
        }
        return errors.findAllByTenantIdAndImportBatchIdAndErrorLevel(ctx.tenantId(), batchId, level, pageable);
    }

    private TenantContext requireRead() {
        return TenantContextHolder.requireActive().requireAny(
                PermissionCodes.IMPORT_READ,
                PermissionCodes.ORG_IMPORT,
                PermissionCodes.POSITION_IMPORT,
                PermissionCodes.METHODOLOGY_IMPORT,
                PermissionCodes.GRADE_IMPORT);
    }
}
