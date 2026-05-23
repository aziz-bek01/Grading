package uz.hrlab.grading.phase2;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.organization.application.CreateDepartmentCommand;
import uz.hrlab.grading.organization.application.CreateDepartmentUseCase;
import uz.hrlab.grading.organization.application.ArchiveDepartmentUseCase;
import uz.hrlab.grading.organization.domain.Department;
import uz.hrlab.grading.organization.domain.DepartmentType;
import uz.hrlab.grading.project.application.CreateProjectCommand;
import uz.hrlab.grading.project.application.CreateProjectUseCase;
import uz.hrlab.grading.project.application.LockProjectUseCase;
import uz.hrlab.grading.project.application.ProjectLockedException;
import uz.hrlab.grading.project.application.UpdateProjectCommand;
import uz.hrlab.grading.project.application.UpdateProjectUseCase;
import uz.hrlab.grading.project.domain.Project;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("audit")
@Tag("integration")
class WorkflowStateTransitionTest extends AbstractIntegrationTest {

    @Autowired CreateProjectUseCase createProject;
    @Autowired UpdateProjectUseCase updateProject;
    @Autowired LockProjectUseCase lockProject;
    @Autowired CreateDepartmentUseCase createDepartment;
    @Autowired ArchiveDepartmentUseCase archiveDepartment;

    private UUID tenantId;

    @BeforeEach
    void setup() {
        tenantId = newSeededTenantId();
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(), Set.of(RoleCodes.HRLAB_SUPER_ADMIN),
                Set.of(PermissionCodes.PROJECT_CREATE, PermissionCodes.PROJECT_EDIT,
                        PermissionCodes.ORG_EDIT),
                Set.of(), false, "ru-RU"));
    }

    @AfterEach
    void teardown() {
        TenantContextHolder.clear();
    }

    @Test
    void lockedProjectRejectsPatch() {
        Project p = createProject.create(new CreateProjectCommand(
                "PRJ-LOCK-1", Map.of("ru-RU", "Test"), null, null, null));
        lockProject.lock(p.id());
        assertThatThrownBy(() -> updateProject.update(p.id(),
                new UpdateProjectCommand(Map.of("ru-RU", "NewName"), null, null, null)))
                .isInstanceOf(ProjectLockedException.class);
    }

    @Test
    void archivedDepartmentCannotBeParent() {
        Project p = createProject.create(new CreateProjectCommand(
                "PRJ-ARCH-1", Map.of("ru-RU", "Test"), null, null, null));
        Department parent = createDepartment.create(new CreateDepartmentCommand(
                p.id(), null, "ROOT", Map.of("ru-RU", "R"), DepartmentType.BRANCH));
        archiveDepartment.archive(parent.id());

        assertThatThrownBy(() -> createDepartment.create(new CreateDepartmentCommand(
                p.id(), parent.id(), "CHILD",
                Map.of("ru-RU", "C"), DepartmentType.UNIT)))
                .hasMessageContaining("archived");
    }
}
