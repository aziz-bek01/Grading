package uz.hrlab.grading.organization.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.organization.domain.Department;
import uz.hrlab.grading.organization.domain.DepartmentStatus;
import uz.hrlab.grading.organization.domain.DepartmentType;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.project.domain.ProjectStatus;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2-FIX (Task 2) — {@link CreateDepartmentUseCase} ABAC enforcement.
 *
 * <p>Mirrors the Update/Archive use cases: when a {@code parentId} is supplied,
 * the create path MUST also pass the department-subtree gate
 * ({@code enforceCanWriteInDepartment}) so a department-scoped writer cannot
 * graft a child under a parent outside their subtree. Root-department creation
 * (no parent) stays gated on the project only.
 */
@ExtendWith(MockitoExtension.class)
class CreateDepartmentUseCaseTest {

    @Mock DepartmentRepository departments;
    @Mock ProjectRepository projects;
    @Mock AuditService audit;
    @Mock AbacGate abacGate;

    CreateDepartmentUseCase useCase;

    UUID tenantId;
    UUID userId;
    UUID projectId;
    UUID parentId;

    @BeforeEach
    void setUp() {
        useCase = new CreateDepartmentUseCase(departments, projects, audit, abacGate);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        parentId = UUID.randomUUID();
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("CLIENT_HR_DIRECTOR"),
                Set.of("ORG_EDIT"), Set.of(), false, "ru-RU"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void deniedParentSubtreeBlocksCreateAndPersistsNothing() {
        // The project gate passes, but the parent department is outside the
        // writer's subtree: the department gate denies → 404, nothing saved.
        when(projects.findByIdAndTenantId(eq(projectId), eq(tenantId)))
                .thenReturn(Optional.of(activeProject()));
        doThrow(new TenantAccessDeniedException())
                .when(abacGate)
                .enforceCanWriteInDepartment(any(), eq(projectId), eq(parentId));

        assertThatThrownBy(() -> useCase.create(commandWithParent()))
                .isInstanceOf(TenantAccessDeniedException.class);

        verify(abacGate).enforceCanWriteInProject(any(), eq(projectId));
        verify(abacGate).enforceCanWriteInDepartment(any(), eq(projectId), eq(parentId));
        verify(departments, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void rootCreationIsGatedOnProjectOnlyNotDepartment() {
        // No parent → only the project gate fires; the department-subtree gate
        // is never consulted, so root creation still works for project-scoped
        // (non department-scoped) writers.
        when(projects.findByIdAndTenantId(eq(projectId), eq(tenantId)))
                .thenReturn(Optional.of(activeProject()));
        when(departments.existsByTenantIdAndProjectIdAndCode(
                eq(tenantId), eq(projectId), any())).thenReturn(false);

        Department created = useCase.create(commandRoot());

        assertThat(created).isNotNull();
        assertThat(created.parentId()).isNull();
        verify(abacGate).enforceCanWriteInProject(any(), eq(projectId));
        verify(abacGate, never()).enforceCanWriteInDepartment(any(), any(), any());
        verify(departments).save(any(DepartmentJpaEntity.class));
    }

    @Test
    void childCreationPassesBothGatesAndPersists() {
        when(projects.findByIdAndTenantId(eq(projectId), eq(tenantId)))
                .thenReturn(Optional.of(activeProject()));
        when(departments.existsByTenantIdAndProjectIdAndCode(
                eq(tenantId), eq(projectId), any())).thenReturn(false);
        // Parent exists for the same tenant/project (validateParentForCreate).
        when(departments.findByIdAndTenantId(eq(parentId), eq(tenantId)))
                .thenReturn(Optional.of(new DepartmentJpaEntity(
                        parentId, tenantId, projectId, null, "ROOT",
                        Map.of("ru-RU", "Root"), DepartmentType.BRANCH,
                        DepartmentStatus.ACTIVE)));

        Department created = useCase.create(commandWithParent());

        assertThat(created.parentId()).isEqualTo(parentId);
        verify(abacGate).enforceCanWriteInProject(any(), eq(projectId));
        verify(abacGate).enforceCanWriteInDepartment(any(), eq(projectId), eq(parentId));
        verify(departments).save(any(DepartmentJpaEntity.class));
    }

    // ---------- helpers ----------

    private ProjectJpaEntity activeProject() {
        return new ProjectJpaEntity(projectId, tenantId, "PRJ-1",
                Map.of("ru-RU", "Project"), null, ProjectStatus.ACTIVE,
                null, null, null);
    }

    private CreateDepartmentCommand commandRoot() {
        return new CreateDepartmentCommand(
                projectId, null, "ROOT", Map.of("ru-RU", "Root"),
                DepartmentType.BRANCH);
    }

    private CreateDepartmentCommand commandWithParent() {
        return new CreateDepartmentCommand(
                projectId, parentId, "CHILD", Map.of("ru-RU", "Child"),
                DepartmentType.DEPARTMENT);
    }
}
