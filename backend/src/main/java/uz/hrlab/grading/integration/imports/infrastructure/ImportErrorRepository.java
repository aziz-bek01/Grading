package uz.hrlab.grading.integration.imports.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.integration.imports.domain.ImportErrorLevel;

import java.util.List;
import java.util.UUID;

public interface ImportErrorRepository
        extends TenantAwareRepository<ImportErrorJpaEntity, UUID> {

    Page<ImportErrorJpaEntity> findAllByTenantIdAndImportBatchId(
            UUID tenantId, UUID importBatchId, Pageable pageable);

    Page<ImportErrorJpaEntity> findAllByTenantIdAndImportBatchIdAndErrorLevel(
            UUID tenantId, UUID importBatchId, ImportErrorLevel level, Pageable pageable);

    long countByTenantIdAndImportBatchIdAndErrorLevel(
            UUID tenantId, UUID importBatchId, ImportErrorLevel level);

    List<ImportErrorJpaEntity> findAllByTenantIdAndImportBatchId(UUID tenantId, UUID importBatchId);
}
