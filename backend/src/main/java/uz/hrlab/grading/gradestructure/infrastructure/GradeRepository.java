package uz.hrlab.grading.gradestructure.infrastructure;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.Collection;
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

    void deleteAll(Iterable<? extends GradeJpaEntity> entities);

    /**
     * Batched grade count per structure for the list view (BE-1, no N+1).
     * Tenant-scoped + restricted to the page's structure ids. Returns
     * {@code [structureId, count]} pairs; structures with zero grades are
     * absent (caller defaults to 0).
     */
    @Query("SELECT g.gradeStructureId, COUNT(g) FROM GradeJpaEntity g " +
            "WHERE g.tenantId = :tenantId AND g.gradeStructureId IN :structureIds " +
            "GROUP BY g.gradeStructureId")
    List<Object[]> countByStructureIds(@Param("tenantId") UUID tenantId,
                                       @Param("structureIds") Collection<UUID> structureIds);
}
