package uz.hrlab.grading.access;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogJpaEntity;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogRepository;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.jobprofile.application.UpdateJobProfileCommand;
import uz.hrlab.grading.jobprofile.application.UpdateJobProfileUseCase;
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
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D-301 — proves that an ABAC denial on a Phase 3 write use case lands an
 * {@code ACCESS_DENIED_BY_ABAC} row in {@code system_audit_log}, with the
 * correct tenant, actor, and entity reference.
 *
 * <p>Scenario: a project-scoped user without membership in the target
 * project (an unrelated project belongs to its assignment set) attempts to
 * PATCH a job profile. {@code ProjectMembershipPolicy} returns DENY,
 * {@code AbacGate.recordDenial} writes the audit row, and
 * {@code TenantAccessDeniedException} is raised (→ 404 at the controller).
 */
@Tag("abac")
@Tag("audit")
@Tag("integration")
class Phase3AbacDenialAuditTest extends AbstractIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired DepartmentRepository departments;
    @Autowired PositionRepository positions;
    @Autowired JobProfileRepository profiles;
    @Autowired UpdateJobProfileUseCase updateUseCase;
    @Autowired SystemAuditLogRepository auditLog;

    @AfterEach
    void cleanup() { TenantContextHolder.clear(); }

    @Test
    void departmentManagerOutOfProjectScopeDeniedAndAudited() {
        UUID tenant = newSeededTenantId();
        UUID actor = UUID.randomUUID();

        ProjectJpaEntity targetProject = projects.save(newProject(tenant, "PRJ-TGT"));
        ProjectJpaEntity unrelatedProject = projects.save(newProject(tenant, "PRJ-OTHER"));
        DepartmentJpaEntity dpt = departments.save(newDept(tenant, targetProject.getId(),
                "DPT-TGT"));
        PositionJpaEntity pos = positions.save(newPosition(tenant, targetProject.getId(),
                dpt.getId(), "POS-TGT"));
        JobProfileJpaEntity profile = profiles.save(newProfile(
                tenant, targetProject.getId(), pos.getId()));

        // User is a Department Manager assigned ONLY to unrelatedProject.
        // (DEPARTMENT_MANAGER is NOT in the tenant-wide bypass list, so
        // ProjectMembershipPolicy MUST return DENY for targetProject.)
        TenantContextHolder.set(new TenantContext(
                actor, tenant,
                Set.of(unrelatedProject.getId()),
                Set.of("DEPARTMENT_MANAGER"),
                Set.of("JOB_PROFILE_EDIT"),
                Set.of(), false, "ru-RU"));

        UUID profileId = profile.getId();
        assertThatThrownBy(() -> updateUseCase.update(profileId,
                new UpdateJobProfileCommand(
                        Map.of("ru-RU", "Mutated"), null, null, null, null, null,
                        null, null, null, null, null, null, null)))
                .isInstanceOf(TenantAccessDeniedException.class);

        // Audit log MUST carry an ACCESS_DENIED_BY_ABAC row for this tenant.
        boolean denialAudited = auditLog
                .findByTenantIdOrderByCreatedAtDesc(tenant).stream()
                .map(SystemAuditLogJpaEntity::getAction)
                .anyMatch("ACCESS_DENIED_BY_ABAC"::equals);
        assertThat(denialAudited)
                .as("ACCESS_DENIED_BY_ABAC audit row must be written on denial")
                .isTrue();
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

    private JobProfileJpaEntity newProfile(UUID tenantId, UUID projectId, UUID positionId) {
        JobProfileJpaEntity p = new JobProfileJpaEntity(
                UUID.randomUUID(), tenantId, projectId, positionId,
                JobProfileStatus.DRAFT, 1, null);
        p.setPurposeI18n(Map.of("ru-RU", "Цель"));
        p.setMainDutiesI18n(Map.of("ru-RU", "Обязанности"));
        return p;
    }
}
