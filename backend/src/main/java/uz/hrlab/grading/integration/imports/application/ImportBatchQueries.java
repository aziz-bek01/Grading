package uz.hrlab.grading.integration.imports.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.integration.imports.api.ImportBatchResponse;
import uz.hrlab.grading.integration.imports.api.ImportErrorResponse;
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

    // BE-035 — returns the wire DTO (mapped in-tx), never the JpaEntity.
    @Transactional(readOnly = true)
    public Page<ImportBatchResponse> list(UUID projectId, ImportBatchStatus status, Pageable pageable) {
        TenantContext ctx = requireRead();
        Page<ImportBatchJpaEntity> page;
        if (status == null) {
            page = batches.findAllByTenantIdAndProjectId(ctx.tenantId(), projectId, pageable);
        } else {
            page = batches.findAllByTenantIdAndProjectIdAndStatus(ctx.tenantId(), projectId, status, pageable);
        }
        return page.map(ImportBatchResponse::from);
    }

    @Transactional(readOnly = true)
    public ImportBatchResponse get(UUID batchId) {
        TenantContext ctx = requireRead();
        return ImportBatchResponse.from(batches.findByIdAndTenantId(batchId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new));
    }

    @Transactional(readOnly = true)
    public Page<ImportBatchRowJpaEntity> listRows(UUID batchId, Pageable pageable) {
        TenantContext ctx = requireRead();
        // ownership probe — same 404 pattern as evaluation
        batches.findByIdAndTenantId(batchId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        return rows.findAllByTenantIdAndImportBatchId(ctx.tenantId(), batchId, pageable);
    }

    // BE-035 — returns the wire DTO (mapped in-tx), never the JpaEntity.
    @Transactional(readOnly = true)
    public Page<ImportErrorResponse> listErrors(UUID batchId, ImportErrorLevel level, Pageable pageable) {
        TenantContext ctx = requireRead();
        batches.findByIdAndTenantId(batchId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        Page<ImportErrorJpaEntity> page;
        if (level == null) {
            page = errors.findAllByTenantIdAndImportBatchId(ctx.tenantId(), batchId, pageable);
        } else {
            page = errors.findAllByTenantIdAndImportBatchIdAndErrorLevel(ctx.tenantId(), batchId, level, pageable);
        }
        return page.map(ImportErrorResponse::from);
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
