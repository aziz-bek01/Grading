package uz.hrlab.grading.methodology.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Methodology container repository — tenant-aware (security-blueprint §5.2,
 * F-01).
 */
public interface MethodologyRepository
        extends TenantAwareRepository<MethodologyJpaEntity, UUID> {

    boolean existsByTenantIdAndProjectIdAndCode(UUID tenantId, UUID projectId, String code);

    Page<MethodologyJpaEntity> findAllByTenantIdAndProjectId(
            UUID tenantId, UUID projectId, Pageable pageable);

    Page<MethodologyJpaEntity> findAllByTenantIdAndProjectIdAndStatusNot(
            UUID tenantId, UUID projectId, MethodologyStatus excluded, Pageable pageable);

    /** Count of methodologies in a tenant — used by the portfolio summary. */
    long countByTenantId(UUID tenantId);

    /**
     * Batched by-id lookup for the "my evaluations" inbox enrichment: resolve a
     * page of distinct {@code methodologyId}s to their container rows in ONE
     * tenant-scoped query (no N+1), for the localized name map. Tenant is pinned,
     * so an id from another tenant contributes no row (not the BOLA-prone
     * {@code findAllById}).
     */
    List<MethodologyJpaEntity> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    /**
     * PART B — evaluator-scoped methodology list: the ACTIVE methodologies among a
     * candidate set of ids (derived from the caller's own evaluations) in a given
     * project. Tenant + project + status are pinned in the predicate; ARCHIVED
     * containers are filtered out by the {@code status} bind.
     */
    List<MethodologyJpaEntity> findAllByTenantIdAndProjectIdAndStatusAndIdIn(
            UUID tenantId, UUID projectId, MethodologyStatus status, Collection<UUID> ids);
}
