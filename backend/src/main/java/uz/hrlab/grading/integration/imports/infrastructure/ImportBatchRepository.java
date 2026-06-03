package uz.hrlab.grading.integration.imports.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.integration.imports.domain.ImportBatchStatus;

import java.util.List;
import java.util.UUID;

/** Tenant-aware repository for import batches (integration-blueprint §8). */
public interface ImportBatchRepository
        extends TenantAwareRepository<ImportBatchJpaEntity, UUID> {

    Page<ImportBatchJpaEntity> findAllByTenantIdAndProjectId(UUID tenantId, UUID projectId, Pageable pageable);

    Page<ImportBatchJpaEntity> findAllByTenantIdAndProjectIdAndStatus(
            UUID tenantId, UUID projectId, ImportBatchStatus status, Pageable pageable);

    List<ImportBatchJpaEntity> findAllByTenantIdAndStatusIn(UUID tenantId, List<ImportBatchStatus> statuses);

    java.util.Optional<ImportBatchJpaEntity> findByIdAndTenantIdAndProjectId(
            UUID id, UUID tenantId, UUID projectId);
}
