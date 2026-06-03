package uz.hrlab.grading.gradestructure.infrastructure;

import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradeRepository extends TenantAwareRepository<GradeJpaEntity, UUID> {

    List<GradeJpaEntity> findAllByTenantIdAndGradeStructureIdOrderBySortOrderAsc(
            UUID tenantId, UUID gradeStructureId);

    Optional<GradeJpaEntity> findByTenantIdAndGradeStructureIdAndGradeNumber(
            UUID tenantId, UUID gradeStructureId, int gradeNumber);

    boolean existsByTenantIdAndGradeStructureIdAndGradeNumber(
            UUID tenantId, UUID gradeStructureId, int gradeNumber);

    void delete(GradeJpaEntity entity);
}
