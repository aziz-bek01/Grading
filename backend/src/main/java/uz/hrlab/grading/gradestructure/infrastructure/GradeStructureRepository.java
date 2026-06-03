package uz.hrlab.grading.gradestructure.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatus;

import java.util.List;
import java.util.UUID;

public interface GradeStructureRepository
        extends TenantAwareRepository<GradeStructureJpaEntity, UUID> {

    boolean existsByTenantIdAndProjectIdAndCode(UUID tenantId, UUID projectId, String code);

    Page<GradeStructureJpaEntity> findAllByTenantIdAndProjectId(
            UUID tenantId, UUID projectId, Pageable pageable);

    Page<GradeStructureJpaEntity> findAllByTenantIdAndProjectIdAndStatus(
            UUID tenantId, UUID projectId, GradeStructureStatus status, Pageable pageable);

    /** Used by evaluation approval to find the active grade structure for a project. */
    List<GradeStructureJpaEntity> findAllByTenantIdAndProjectIdAndStatusOrderByVersionNumberDesc(
            UUID tenantId, UUID projectId, GradeStructureStatus status);
}
