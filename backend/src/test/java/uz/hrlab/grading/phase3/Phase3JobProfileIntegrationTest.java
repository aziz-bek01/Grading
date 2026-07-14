package uz.hrlab.grading.phase3;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatus;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileJpaEntity;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileRepository;
import uz.hrlab.grading.organization.domain.DepartmentStatus;
import uz.hrlab.grading.organization.domain.DepartmentType;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.project.domain.ProjectStatus;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tenant-isolation + immutability proofs for {@link JobProfileJpaEntity} at
 * the repository level (Phase 3 release-gate).
 */
@Tag("tenant-isolation")
@Tag("integration")
class Phase3JobProfileIntegrationTest extends AbstractIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired DepartmentRepository departments;
    @Autowired PositionRepository positions;
    @Autowired JobProfileRepository profiles;

    @Test
    void profileFromTenantBIsInvisibleToTenantA() {
        UUID tenantA = newSeededTenantId();
        UUID tenantB = newSeededTenantId();
        ProjectJpaEntity projB = projects.save(newProject(tenantB, "PRJ-PB"));
        DepartmentJpaEntity dptB = departments.save(newDept(tenantB, projB.getId(), "DPT-PB"));
        PositionJpaEntity posB = positions.save(newPosition(tenantB, projB.getId(),
                dptB.getId(), "POS-PB"));
        JobProfileJpaEntity profileB = profiles.save(newProfile(
                tenantB, projB.getId(), posB.getId(), JobProfileStatus.DRAFT, 1, null));

        Optional<JobProfileJpaEntity> probe =
                profiles.findByIdAndTenantId(profileB.getId(), tenantA);
        assertThat(probe).isEmpty();
    }

    @Test
    void partialUniqueIndexBlocksDoubleActiveProfile() {
        UUID tenant = newSeededTenantId();
        ProjectJpaEntity proj = projects.save(newProject(tenant, "PRJ-UQ"));
        DepartmentJpaEntity dpt = departments.save(newDept(tenant, proj.getId(), "DPT-UQ"));
        PositionJpaEntity pos = positions.save(newPosition(tenant, proj.getId(),
                dpt.getId(), "POS-UQ"));
        profiles.save(newProfile(tenant, proj.getId(), pos.getId(),
                JobProfileStatus.DRAFT, 1, null));

        assertThatThrownBy(() ->
                profiles.save(newProfile(tenant, proj.getId(), pos.getId(),
                        JobProfileStatus.DRAFT, 2, null)))
                .hasMessageContaining("uq_job_profiles_position_active");
    }

    @Test
    void archivedRevisionsCanCoexist() {
        UUID tenant = newSeededTenantId();
        ProjectJpaEntity proj = projects.save(newProject(tenant, "PRJ-ARC"));
        DepartmentJpaEntity dpt = departments.save(newDept(tenant, proj.getId(), "DPT-ARC"));
        PositionJpaEntity pos = positions.save(newPosition(tenant, proj.getId(),
                dpt.getId(), "POS-ARC"));
        JobProfileJpaEntity r1 = profiles.save(newProfile(tenant, proj.getId(), pos.getId(),
                JobProfileStatus.ARCHIVED, 1, null));
        JobProfileJpaEntity r2 = profiles.save(newProfile(tenant, proj.getId(), pos.getId(),
                JobProfileStatus.ARCHIVED, 2, r1.getId()));
        JobProfileJpaEntity active = profiles.save(newProfile(tenant, proj.getId(),
                pos.getId(), JobProfileStatus.DRAFT, 3, r2.getId()));

        assertThat(profiles
                .findAllByTenantIdAndProjectIdAndPositionIdOrderByRevisionNumberDesc(
                        tenant, proj.getId(), pos.getId()))
                .extracting(JobProfileJpaEntity::getRevisionNumber)
                .containsExactly(3, 2, 1);
        assertThat(profiles
                .findFirstByTenantIdAndProjectIdAndPositionIdAndStatusNot(
                        tenant, proj.getId(), pos.getId(), JobProfileStatus.ARCHIVED))
                .map(JobProfileJpaEntity::getId)
                .contains(active.getId());
    }

    /**
     * Anti-BOLA proof for the bulk finder behind
     * {@link uz.hrlab.grading.jobprofile.application.FindJobProfileQuery#findActiveStatusesByPositionIds}:
     * even when Tenant B's position id is mixed into the query's IN-list, the
     * {@code tenant_id} predicate keeps Tenant B's active profile from leaking
     * into Tenant A's result.
     */
    @Test
    void bulkFinderReturnsOnlyTenantARowsForMixedPositionIdList() {
        UUID tenantA = newSeededTenantId();
        UUID tenantB = newSeededTenantId();
        ProjectJpaEntity projA = projects.save(newProject(tenantA, "PRJ-BA"));
        ProjectJpaEntity projB = projects.save(newProject(tenantB, "PRJ-BB"));
        DepartmentJpaEntity dptA = departments.save(newDept(tenantA, projA.getId(), "DPT-BA"));
        DepartmentJpaEntity dptB = departments.save(newDept(tenantB, projB.getId(), "DPT-BB"));
        PositionJpaEntity posA1 = positions.save(newPosition(tenantA, projA.getId(),
                dptA.getId(), "POS-BA1"));
        PositionJpaEntity posA2 = positions.save(newPosition(tenantA, projA.getId(),
                dptA.getId(), "POS-BA2"));
        PositionJpaEntity posB = positions.save(newPosition(tenantB, projB.getId(),
                dptB.getId(), "POS-BB"));

        JobProfileJpaEntity profA1 = profiles.save(newProfile(tenantA, projA.getId(),
                posA1.getId(), JobProfileStatus.APPROVED, 1, null));
        JobProfileJpaEntity profA2 = profiles.save(newProfile(tenantA, projA.getId(),
                posA2.getId(), JobProfileStatus.DRAFT, 1, null));
        JobProfileJpaEntity profB = profiles.save(newProfile(tenantB, projB.getId(),
                posB.getId(), JobProfileStatus.APPROVED, 1, null));

        // Mixed id list deliberately includes Tenant B's position id.
        List<UUID> mixedIds = List.of(posA1.getId(), posA2.getId(), posB.getId());

        List<JobProfileJpaEntity> rows = profiles.findAllByTenantIdAndPositionIdInAndStatusNot(
                tenantA, mixedIds, JobProfileStatus.ARCHIVED);

        assertThat(rows).extracting(JobProfileJpaEntity::getId)
                .containsExactlyInAnyOrder(profA1.getId(), profA2.getId())
                .doesNotContain(profB.getId());
        assertThat(rows).extracting(JobProfileJpaEntity::getTenantId)
                .containsOnly(tenantA);
    }

    private ProjectJpaEntity newProject(UUID tenantId, String code) {
        return new ProjectJpaEntity(
                UUID.randomUUID(), tenantId, code, Map.of("ru-RU", code), null,
                ProjectStatus.ACTIVE, null, null, null);
    }

    private DepartmentJpaEntity newDept(UUID tenantId, UUID projectId, String code) {
        return new DepartmentJpaEntity(
                UUID.randomUUID(), tenantId, projectId, null, code, Map.of("ru-RU", code),
                DepartmentType.DEPARTMENT, DepartmentStatus.ACTIVE);
    }

    private PositionJpaEntity newPosition(UUID tenantId, UUID projectId, UUID deptId,
                                          String code) {
        return new PositionJpaEntity(
                UUID.randomUUID(), tenantId, projectId, deptId, code,
                Map.of("ru-RU", code), null, null, null, null, PositionStatus.ACTIVE);
    }

    private JobProfileJpaEntity newProfile(UUID tenantId, UUID projectId, UUID positionId,
                                           JobProfileStatus status, int revision,
                                           UUID previousRevisionId) {
        JobProfileJpaEntity e = new JobProfileJpaEntity(
                UUID.randomUUID(), tenantId, projectId, positionId, status, revision,
                previousRevisionId);
        e.setPurposeI18n(Map.of("ru-RU", "Цель"));
        e.setMainDutiesI18n(Map.of("ru-RU", "Обязанности"));
        return e;
    }
}
