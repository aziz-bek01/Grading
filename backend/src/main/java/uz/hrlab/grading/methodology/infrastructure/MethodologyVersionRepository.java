package uz.hrlab.grading.methodology.infrastructure;

import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** MethodologyVersion repository — tenant-aware. */
public interface MethodologyVersionRepository
        extends TenantAwareRepository<MethodologyVersionJpaEntity, UUID> {

    List<MethodologyVersionJpaEntity>
            findAllByTenantIdAndMethodologyIdOrderByVersionNumberDesc(
                    UUID tenantId, UUID methodologyId);

    /**
     * Batched variant of the per-methodology version lookup — fetches every
     * version of MANY methodologies in one query (avoids N+1 in the list view),
     * ordered version-number-descending so callers can take the first row as the
     * latest version and scan for the latest APPROVED/LOCKED ("active") one.
     */
    List<MethodologyVersionJpaEntity>
            findAllByTenantIdAndMethodologyIdInOrderByVersionNumberDesc(
                    UUID tenantId, Collection<UUID> methodologyIds);

    Optional<MethodologyVersionJpaEntity>
            findFirstByTenantIdAndMethodologyIdOrderByVersionNumberDesc(
                    UUID tenantId, UUID methodologyId);

    /**
     * Batched by-id lookup for the "my evaluations" inbox enrichment: resolve a
     * page of distinct {@code methodologyVersionId}s to their version rows in ONE
     * tenant-scoped query (no N+1). Tenant is pinned, so an id from another tenant
     * simply contributes no row (never widens scope — not the BOLA-prone
     * {@code findAllById}).
     */
    List<MethodologyVersionJpaEntity> findAllByTenantIdAndIdIn(
            UUID tenantId, Collection<UUID> ids);

    boolean existsByTenantIdAndMethodologyIdAndVersionNumber(
            UUID tenantId, UUID methodologyId, int versionNumber);
}
