package uz.hrlab.grading.methodology.infrastructure;

import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.methodology.domain.MethodologyTemplateStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for tenant-defined (DB-backed) CUSTOM methodology templates
 * (Epic E) — tenant-aware (security-blueprint §5.2, F-01). Extends
 * {@link TenantAwareRepository} like its methodology siblings (the table HAS
 * RLS, unlike the access control-plane repos), so the only legal access paths
 * are tenant-bound and {@code findById(id)} is NOT inherited.
 *
 * <p>Every read/write here is additionally constrained by the explicit tenant
 * predicate (defense-in-depth alongside the RLS policy on the table). A
 * cross-tenant id therefore resolves to {@link Optional#empty()} → 404.
 */
public interface CustomMethodologyTemplateRepository
        extends TenantAwareRepository<MethodologyTemplateJpaEntity, UUID> {

    /** A tenant's templates filtered by status (ACTIVE for the picker). */
    List<MethodologyTemplateJpaEntity> findByTenantIdAndStatus(
            UUID tenantId, MethodologyTemplateStatus status);

    /** Resolve a custom template by its per-tenant stable code. */
    Optional<MethodologyTemplateJpaEntity> findByTenantIdAndCode(UUID tenantId, String code);

    /** Duplicate-code guard for save-as-template (UNIQUE (tenant_id, code)). */
    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
