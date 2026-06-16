package uz.hrlab.grading.reporting.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.reporting.domain.ReportStatus;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.time.OffsetDateTime;
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

    /**
     * Batch-4 re-queuer scan: retryable {@code FAILED} reports for the tenant
     * that are DUE and still under the retry bound. Tenant-scoped (RLS-bound).
     */
    @Query("""
            select r from ReportJpaEntity r
            where r.tenantId = :tenantId
              and r.status = uz.hrlab.grading.reporting.domain.ReportStatus.FAILED
              and r.attemptCount < :maxAttempts
              and (r.nextAttemptAt is null or r.nextAttemptAt <= :now)
            """)
    List<ReportJpaEntity> findRetryDue(@Param("tenantId") UUID tenantId,
                                       @Param("now") OffsetDateTime now,
                                       @Param("maxAttempts") int maxAttempts);
}
