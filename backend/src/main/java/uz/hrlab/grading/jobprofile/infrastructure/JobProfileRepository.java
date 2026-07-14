package uz.hrlab.grading.jobprofile.infrastructure;

import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-aware repository for {@link JobProfileJpaEntity} (security-blueprint
 * §5.2). Only tenant-bound lookups exposed.
 */
public interface JobProfileRepository
        extends TenantAwareRepository<JobProfileJpaEntity, UUID> {

    /**
     * Returns the currently-active (non-ARCHIVED) profile for a position, if
     * any. Backed by the partial unique index
     * {@code uq_job_profiles_position_active}.
     */
    Optional<JobProfileJpaEntity>
            findFirstByTenantIdAndProjectIdAndPositionIdAndStatusNot(
                    UUID tenantId, UUID projectId, UUID positionId,
                    JobProfileStatus status);

    /**
     * Full revision history for a position, newest revision first. ARCHIVED
     * revisions ARE included.
     */
    List<JobProfileJpaEntity>
            findAllByTenantIdAndProjectIdAndPositionIdOrderByRevisionNumberDesc(
                    UUID tenantId, UUID projectId, UUID positionId);

    boolean existsByTenantIdAndProjectIdAndPositionIdAndStatusNot(
            UUID tenantId, UUID projectId, UUID positionId, JobProfileStatus status);

    /**
     * PAGE-2 BE — tenant-scoped batch load of the currently-active (non-ARCHIVED)
     * profile for a SET of positions, one page of the Positions Catalog at a
     * time. Backed by the SAME partial unique index
     * {@code uq_job_profiles_position_active} as
     * {@link #findFirstByTenantIdAndProjectIdAndPositionIdAndStatusNot}, so it
     * returns AT MOST one row per position.
     *
     * <p>{@code tenant_id} is always pinned, so any position id belonging to
     * another tenant simply contributes no row — this mirrors the anti-BOLA
     * batch pattern of {@code PositionRepository.findAllByTenantIdAndIdIn}
     * (PERF-1/PERF-2) and is never the scope-widening {@code findAllById}.
     */
    List<JobProfileJpaEntity> findAllByTenantIdAndPositionIdInAndStatusNot(
            UUID tenantId, Collection<UUID> positionIds, JobProfileStatus status);
}
