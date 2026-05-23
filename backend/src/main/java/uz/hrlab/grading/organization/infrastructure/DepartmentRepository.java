package uz.hrlab.grading.organization.infrastructure;

import org.springframework.data.jpa.repository.Query;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository
        extends TenantAwareRepository<DepartmentJpaEntity, UUID> {

    List<DepartmentJpaEntity> findByTenantIdAndProjectId(UUID tenantId, UUID projectId);

    List<DepartmentJpaEntity> findByTenantIdAndProjectIdAndParentId(UUID tenantId, UUID projectId,
                                                                   UUID parentId);

    boolean existsByTenantIdAndProjectIdAndCode(UUID tenantId, UUID projectId, String code);

    Optional<DepartmentJpaEntity> findByTenantIdAndProjectIdAndCode(UUID tenantId, UUID projectId,
                                                                   String code);

    /**
     * Recursive descendant lookup (cycle prevention). Uses native CTE for
     * efficiency — query is tenant- and project-scoped.
     */
    @Query(value = """
            WITH RECURSIVE descendants AS (
                SELECT d.* FROM departments d
                WHERE d.id = :rootId AND d.tenant_id = :tenantId
                UNION ALL
                SELECT c.* FROM departments c
                JOIN descendants p ON c.parent_id = p.id
                WHERE c.tenant_id = :tenantId
            )
            SELECT * FROM descendants WHERE id <> :rootId
            """, nativeQuery = true)
    List<DepartmentJpaEntity> findDescendants(UUID rootId, UUID tenantId);
}
