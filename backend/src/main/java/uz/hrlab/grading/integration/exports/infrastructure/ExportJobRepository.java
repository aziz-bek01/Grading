package uz.hrlab.grading.integration.exports.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.integration.exports.domain.ExportJobStatus;
import uz.hrlab.grading.integration.exports.domain.ExportType;

import java.util.List;
import java.util.UUID;

public interface ExportJobRepository
        extends TenantAwareRepository<ExportJobJpaEntity, UUID> {

    Page<ExportJobJpaEntity> findAllByTenantIdAndProjectId(
            UUID tenantId, UUID projectId, Pageable pageable);

    Page<ExportJobJpaEntity> findAllByTenantIdAndProjectIdAndStatus(
            UUID tenantId, UUID projectId, ExportJobStatus status, Pageable pageable);

    Page<ExportJobJpaEntity> findAllByTenantIdAndProjectIdAndExportType(
            UUID tenantId, UUID projectId, ExportType type, Pageable pageable);

    List<ExportJobJpaEntity> findAllByTenantIdAndStatusIn(UUID tenantId, List<ExportJobStatus> statuses);
}
