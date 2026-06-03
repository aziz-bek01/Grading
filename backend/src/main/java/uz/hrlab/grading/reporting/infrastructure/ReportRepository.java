package uz.hrlab.grading.reporting.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.reporting.domain.ReportStatus;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.util.List;
import java.util.UUID;

public interface ReportRepository
        extends TenantAwareRepository<ReportJpaEntity, UUID> {

    Page<ReportJpaEntity> findAllByTenantIdAndProjectId(
            UUID tenantId, UUID projectId, Pageable pageable);

    Page<ReportJpaEntity> findAllByTenantIdAndProjectIdAndStatus(
            UUID tenantId, UUID projectId, ReportStatus status, Pageable pageable);

    Page<ReportJpaEntity> findAllByTenantIdAndProjectIdAndReportType(
            UUID tenantId, UUID projectId, ReportType type, Pageable pageable);

    Page<ReportJpaEntity> findAllByTenantIdAndRequestedBy(
            UUID tenantId, UUID requestedBy, Pageable pageable);

    List<ReportJpaEntity> findAllByTenantIdAndStatusIn(
            UUID tenantId, List<ReportStatus> statuses);
}
