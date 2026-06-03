package uz.hrlab.grading.gradestructure.infrastructure;

import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradeBandRepository extends TenantAwareRepository<GradeBandJpaEntity, UUID> {

    Optional<GradeBandJpaEntity> findByTenantIdAndGradeId(UUID tenantId, UUID gradeId);

    List<GradeBandJpaEntity> findAllByTenantIdAndGradeStructureIdOrderByMinScoreAsc(
            UUID tenantId, UUID gradeStructureId);

    void delete(GradeBandJpaEntity entity);
}
