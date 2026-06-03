package uz.hrlab.grading.integration.imports.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.integration.imports.domain.ImportBatchRowStatus;

import java.util.List;
import java.util.UUID;

public interface ImportBatchRowRepository
        extends TenantAwareRepository<ImportBatchRowJpaEntity, UUID> {

    Page<ImportBatchRowJpaEntity> findAllByTenantIdAndImportBatchId(
            UUID tenantId, UUID importBatchId, Pageable pageable);

    Page<ImportBatchRowJpaEntity> findAllByTenantIdAndImportBatchIdAndStatus(
            UUID tenantId, UUID importBatchId, ImportBatchRowStatus status, Pageable pageable);

    List<ImportBatchRowJpaEntity> findAllByTenantIdAndImportBatchIdAndStatusIn(
            UUID tenantId, UUID importBatchId, List<ImportBatchRowStatus> statuses);
}
