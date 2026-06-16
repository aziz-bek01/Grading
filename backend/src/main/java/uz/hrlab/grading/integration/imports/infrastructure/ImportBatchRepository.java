package uz.hrlab.grading.integration.imports.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.integration.imports.domain.ImportBatchStatus;

import java.time.OffsetDateTime;
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

    /**
     * Batch-4 re-queuer scan: batches for the tenant in the retryable
     * {@code FAILED} state (TRANSIENT processing failure) that are DUE and still
     * under the retry bound. Tenant-scoped (RLS-bound). Deliberately excludes
     * VALIDATION_FAILED / SCAN_FAILED — those are deterministic outcomes, not
     * transient failures, and must never be auto-retried.
     */
    @Query("""
            select b from ImportBatchJpaEntity b
            where b.tenantId = :tenantId
              and b.status = uz.hrlab.grading.integration.imports.domain.ImportBatchStatus.FAILED
              and b.attemptCount < :maxAttempts
              and (b.nextAttemptAt is null or b.nextAttemptAt <= :now)
            """)
    List<ImportBatchJpaEntity> findRetryDue(@Param("tenantId") UUID tenantId,
                                            @Param("now") OffsetDateTime now,
                                            @Param("maxAttempts") int maxAttempts);
}
