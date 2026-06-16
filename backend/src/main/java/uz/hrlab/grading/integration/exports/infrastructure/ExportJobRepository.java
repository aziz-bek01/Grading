package uz.hrlab.grading.integration.exports.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.integration.exports.domain.ExportJobStatus;
import uz.hrlab.grading.integration.exports.domain.ExportType;

import java.time.OffsetDateTime;
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

    /**
     * Batch-4 re-queuer scan: rows for the given tenant in the retryable
     * {@code FAILED} state that are DUE ({@code next_attempt_at} null = due now,
     * or {@code <= :now}) and still UNDER the retry bound. Tenant-scoped — the
     * re-queuer sets {@code app.tenant_id} before calling so forced RLS applies.
     */
    @Query("""
            select j from ExportJobJpaEntity j
            where j.tenantId = :tenantId
              and j.status = uz.hrlab.grading.integration.exports.domain.ExportJobStatus.FAILED
              and j.attemptCount < :maxAttempts
              and (j.nextAttemptAt is null or j.nextAttemptAt <= :now)
            """)
    List<ExportJobJpaEntity> findRetryDue(@Param("tenantId") UUID tenantId,
                                          @Param("now") OffsetDateTime now,
                                          @Param("maxAttempts") int maxAttempts);
}
