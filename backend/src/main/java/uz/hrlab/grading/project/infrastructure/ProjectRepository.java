package uz.hrlab.grading.project.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.project.domain.ProjectStatus;

import java.util.UUID;

/**
 * Project repository — tenant-aware (security-blueprint §5.2, F-01).
 * NO bare {@code findById} ever; only tenant-bound lookups.
 */
public interface ProjectRepository
        extends TenantAwareRepository<ProjectJpaEntity, UUID> {

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    Page<ProjectJpaEntity> findAllByTenantIdAndStatusNot(UUID tenantId, ProjectStatus excluded,
                                                        Pageable pageable);
}
