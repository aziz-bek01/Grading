package uz.hrlab.grading.jobprofile.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogJpaEntity;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogRepository;
import uz.hrlab.grading.jobprofile.domain.JobProfile;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatus;
import uz.hrlab.grading.jobprofile.domain.JobProfileTransitionRejectedException;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D-305 — end-to-end test of {@link CreateJobProfileRevisionUseCase}: APPROVED
 * source row stays UNCHANGED, new DRAFT row has correct revisionNumber +
 * previousRevisionId, audit row JOB_PROFILE_REVISION_CREATED is written.
 */
@Tag("workflow")
@Tag("integration")
class Phase3RevisionChainTest extends AbstractIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired DepartmentRepository departments;
    @Autowired PositionRepository positions;
    @Autowired JobProfileRepository profiles;
    @Autowired CreateJobProfileRevisionUseCase revisionUseCase;
    @Autowired SystemAuditLogRepository auditLog;

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void createRevisionDoesNotMutateSourceAndCreatesNewDraft() {
        UUID tenant = newSeededTenantId();
        UUID actor = UUID.randomUUID();
        ProjectJpaEntity proj = projects.save(newProject(tenant, "PRJ-REV"));
        DepartmentJpaEntity dpt = departments.save(newDept(tenant, proj.getId(), "DPT-REV"));
        PositionJpaEntity pos = positions.save(newPosition(tenant, proj.getId(),
                dpt.getId(), "POS-REV"));

        // Seed an APPROVED revision 1 directly.
        JobProfileJpaEntity source = new JobProfileJpaEntity(
                UUID.randomUUID(), tenant, proj.getId(), pos.getId(),
                JobProfileStatus.APPROVED, 1, null);
        source.setPurposeI18n(Map.of("ru-RU", "Цель"));
        source.setMainDutiesI18n(Map.of("ru-RU", "Обязанности"));
        source.setApprovedAt(OffsetDateTime.now().minusDays(1));
        source.setApprovedBy(actor);
        source.setLockedAt(OffsetDateTime.now().minusDays(1));
        source = profiles.save(source);

        // Snapshot expected-immutable fields before the call.
        UUID sourceId = source.getId();
        JobProfileStatus sourceStatusBefore = source.getStatus();
        int sourceRevBefore = source.getRevisionNumber();
        Map<String, String> sourcePurposeBefore = Map.copyOf(source.getPurposeI18n());

        TenantContextHolder.set(new TenantContext(
                actor, tenant, Set.of(proj.getId()),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("JOB_PROFILE_EDIT", "JOB_PROFILE_APPROVE"),
                Set.of(), false, "ru-RU"));

        JobProfile revision = revisionUseCase.createRevision(sourceId);

        assertThat(revision.status()).isEqualTo(JobProfileStatus.DRAFT);
        assertThat(revision.revisionNumber()).isEqualTo(2);
        assertThat(revision.previousRevisionId()).isEqualTo(sourceId);
        assertThat(revision.id()).isNotEqualTo(sourceId);
        // Content deep-copied from source.
        assertThat(revision.purposeI18n()).containsEntry("ru-RU", "Цель");

        // Source MUST be unchanged — re-read from DB.
        JobProfileJpaEntity sourceReread = profiles
                .findByIdAndTenantId(sourceId, tenant)
                .orElseThrow();
        assertThat(sourceReread.getStatus()).isEqualTo(sourceStatusBefore);
        assertThat(sourceReread.getRevisionNumber()).isEqualTo(sourceRevBefore);
        assertThat(sourceReread.getPurposeI18n()).isEqualTo(sourcePurposeBefore);

        // Descending history: revision 2 first, then revision 1.
        List<JobProfileJpaEntity> history = profiles
                .findAllByTenantIdAndProjectIdAndPositionIdOrderByRevisionNumberDesc(
                        tenant, proj.getId(), pos.getId());
        assertThat(history).extracting(JobProfileJpaEntity::getRevisionNumber)
                .containsExactly(2, 1);

        // Audit row JOB_PROFILE_REVISION_CREATED present.
        boolean revisionAuditFound = auditLog
                .findByTenantIdOrderByCreatedAtDesc(tenant).stream()
                .map(SystemAuditLogJpaEntity::getAction)
                .anyMatch(AuditAction.JOB_PROFILE_REVISION_CREATED::equals);
        assertThat(revisionAuditFound)
                .as("JOB_PROFILE_REVISION_CREATED audit row must exist")
                .isTrue();
    }

    @Test
    void cannotCreateRevisionFromDraft() {
        UUID tenant = newSeededTenantId();
        ProjectJpaEntity proj = projects.save(newProject(tenant, "PRJ-DR"));
        DepartmentJpaEntity dpt = departments.save(newDept(tenant, proj.getId(), "DPT-DR"));
        PositionJpaEntity pos = positions.save(newPosition(tenant, proj.getId(),
                dpt.getId(), "POS-DR"));
        JobProfileJpaEntity source = new JobProfileJpaEntity(
                UUID.randomUUID(), tenant, proj.getId(), pos.getId(),
                JobProfileStatus.DRAFT, 1, null);
        source.setPurposeI18n(Map.of("ru-RU", "Цель"));
        source.setMainDutiesI18n(Map.of("ru-RU", "Обязанности"));
        UUID sourceId = profiles.save(source).getId();

        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenant, Set.of(proj.getId()),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("JOB_PROFILE_EDIT", "JOB_PROFILE_APPROVE"),
                Set.of(), false, "ru-RU"));

        assertThatThrownBy(() -> revisionUseCase.createRevision(sourceId))
                .isInstanceOf(JobProfileTransitionRejectedException.class);
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
}
