package uz.hrlab.grading.phase2;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import uz.hrlab.grading.AbstractIntegrationTest;
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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-tenant isolation proof for Phase 2 repositories
 * (qa-review §"add @Tag(\"tenant-isolation\") tests").
 *
 * <p>Asserts that every {@code findByIdAndTenantId} on a Tenant-A row with
 * Tenant-B's id returns empty — no leakage at the repository surface.
 */
@Tag("tenant-isolation")
@Tag("integration")
class Phase2TenantIsolationIntegrationTest extends AbstractIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired DepartmentRepository departments;
    @Autowired PositionRepository positions;

    @Test
    void projectFromTenantBIsInvisibleToTenantA() {
        UUID tenantA = newSeededTenantId();
        UUID tenantB = newSeededTenantId();
        ProjectJpaEntity projectB = projects.save(newProject(tenantB, "PRJ-B"));

        Optional<ProjectJpaEntity> probe =
                projects.findByIdAndTenantId(projectB.getId(), tenantA);
        assertThat(probe).isEmpty();
    }

    @Test
    void departmentFromTenantBIsInvisibleToTenantA() {
        UUID tenantA = newSeededTenantId();
        UUID tenantB = newSeededTenantId();
        ProjectJpaEntity projectB = projects.save(newProject(tenantB, "PRJ-B2"));
        DepartmentJpaEntity deptB = departments.save(newDept(tenantB, projectB.getId(), "DPT-B"));

        Optional<DepartmentJpaEntity> probe =
                departments.findByIdAndTenantId(deptB.getId(), tenantA);
        assertThat(probe).isEmpty();
    }

    @Test
    void positionFromTenantBIsInvisibleToTenantA() {
        UUID tenantA = newSeededTenantId();
        UUID tenantB = newSeededTenantId();
        ProjectJpaEntity projectB = projects.save(newProject(tenantB, "PRJ-B3"));
        DepartmentJpaEntity deptB = departments.save(newDept(tenantB, projectB.getId(), "DPT-B3"));
        PositionJpaEntity posB = positions.save(newPosition(
                tenantB, projectB.getId(), deptB.getId(), "POS-B3"));

        Optional<PositionJpaEntity> probe =
                positions.findByIdAndTenantId(posB.getId(), tenantA);
        assertThat(probe).isEmpty();
    }

    @Test
    void positionSearchOnOtherTenantProjectReturnsEmpty() {
        UUID tenantA = newSeededTenantId();
        UUID tenantB = newSeededTenantId();
        ProjectJpaEntity projectB = projects.save(newProject(tenantB, "PRJ-B4"));
        DepartmentJpaEntity deptB = departments.save(newDept(tenantB, projectB.getId(), "DPT-B4"));
        positions.save(newPosition(tenantB, projectB.getId(), deptB.getId(), "POS-B4"));

        Page<PositionJpaEntity> result = positions.search(
                tenantA, projectB.getId(), null, null, null, PageRequest.of(0, 20));
        assertThat(result.getContent()).isEmpty();
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

    private PositionJpaEntity newPosition(UUID tenantId, UUID projectId, UUID departmentId,
                                          String code) {
        return new PositionJpaEntity(
                UUID.randomUUID(), tenantId, projectId, departmentId, code,
                Map.of("ru-RU", code), null, null, null, null, PositionStatus.ACTIVE);
    }
}
