package uz.hrlab.grading.phase2;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.organization.application.CreateDepartmentCommand;
import uz.hrlab.grading.organization.application.CreateDepartmentUseCase;
import uz.hrlab.grading.organization.domain.Department;
import uz.hrlab.grading.organization.domain.DepartmentType;
import uz.hrlab.grading.position.application.CreatePositionCommand;
import uz.hrlab.grading.position.application.CreatePositionUseCase;
import uz.hrlab.grading.project.application.CreateProjectCommand;
import uz.hrlab.grading.project.application.CreateProjectUseCase;
import uz.hrlab.grading.project.domain.Project;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("tenant-isolation")
@Tag("integration")
class CrossResourceTenantValidationTest extends AbstractIntegrationTest {

    @Autowired CreateProjectUseCase createProject;
    @Autowired CreateDepartmentUseCase createDepartment;
    @Autowired CreatePositionUseCase createPosition;

    private UUID tenantId;

    @BeforeEach
    void setup() {
        tenantId = newSeededTenantId();
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(), Set.of(RoleCodes.HRLAB_SUPER_ADMIN),
                Set.of(PermissionCodes.PROJECT_CREATE, PermissionCodes.ORG_EDIT,
                        PermissionCodes.POSITION_CREATE),
                Set.of(), false, "ru-RU"));
    }

    @AfterEach
    void teardown() {
        TenantContextHolder.clear();
    }

    @Test
    void positionCannotReferenceDepartmentFromDifferentProject() {
        Project p1 = createProject.create(new CreateProjectCommand(
                "PRJ-X1", Map.of("ru-RU", "X1"), null, null, null));
        Project p2 = createProject.create(new CreateProjectCommand(
                "PRJ-X2", Map.of("ru-RU", "X2"), null, null, null));
        Department deptInP1 = createDepartment.create(new CreateDepartmentCommand(
                p1.id(), null, "ROOT-X1", Map.of("ru-RU", "R"), DepartmentType.BRANCH));

        // Try to create a position in P2 that references a department in P1.
        assertThatThrownBy(() -> createPosition.create(new CreatePositionCommand(
                p2.id(), deptInP1.id(), "POS-X",
                Map.of("ru-RU", "T"), null, null, null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not found");
    }
}
