package uz.hrlab.grading.gradestructure.infrastructure;

import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.gradestructure.domain.GradeTemplateStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for tenant-defined (DB-backed) CUSTOM grade templates (BE-7) —
 * tenant-aware (security-blueprint §5.2, F-01). Extends
 * {@link TenantAwareRepository} like its grade siblings (the table HAS RLS), so
 * the only legal access paths are tenant-bound and {@code findById(id)} is NOT
 * inherited. EXACT mirror of {@code CustomMethodologyTemplateRepository}.
 *
 * <p>Every read/write here is additionally constrained by the explicit tenant
 * predicate (defense-in-depth alongside the RLS policy on the table). A
 * cross-tenant id therefore resolves to {@link Optional#empty()} → 404.
 */
public interface CustomGradeTemplateRepository
        extends TenantAwareRepository<GradeTemplateJpaEntity, UUID> {

    /** A tenant's templates filtered by status (ACTIVE for the picker). */
    List<GradeTemplateJpaEntity> findByTenantIdAndStatus(
            UUID tenantId, GradeTemplateStatus status);

    /** Resolve a custom template by its per-tenant stable code. */
    Optional<GradeTemplateJpaEntity> findByTenantIdAndCode(UUID tenantId, String code);

    /** Duplicate-code guard for save-as-template (UNIQUE (tenant_id, code)). */
    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
