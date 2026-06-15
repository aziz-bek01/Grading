package uz.hrlab.grading.methodology.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;

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
}
