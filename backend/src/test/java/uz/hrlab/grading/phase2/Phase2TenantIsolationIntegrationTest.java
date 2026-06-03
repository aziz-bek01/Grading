package uz.hrlab.grading.phase2;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.organization.domain.DepartmentStatus;
import uz.hrlab.grading.organization.domain.DepartmentType;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.project.application.FindProjectQuery;
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
    @Autowired FindProjectQuery findProjectQuery;

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

    /**
     * TI-Reg-01 (cross-tenant resource ID forge at the HTTP/use-case layer).
     * Tenant B owns project P-Beta. An ACME-context caller issues
     * {@code findById(P-Beta)} via the {@link FindProjectQuery} (the same
     * code path that backs {@code GET /api/v1/projects/{id}}). Contract:
     * <ol>
     *   <li>{@link TenantAccessDeniedException} is raised — translated to 404
     *       by {@code GlobalExceptionHandler} (no existence revealing);</li>
     *   <li>the exception handler emits a
     *       {@code CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT} audit row (proved
     *       separately by {@code CrossTenantAuditRecordingTest});</li>
     *   <li>the legitimate tenant (Beta) can still load its own project — no
     *       collateral lockout.</li>
     * </ol>
     */
    @Test
    void crossTenantProjectIdForgeAtUseCaseLayerIsDenied() {
        UUID tenantAcme = newSeededTenantId();
        UUID tenantBeta = newSeededTenantId();
        UUID userId = UUID.randomUUID();
        ProjectJpaEntity projectBeta = projects.save(newProject(tenantBeta, "PRJ-BETA-001"));

        // Caller authenticated as a member of ACME.
        TenantContextHolder.set(new TenantContext(
                userId, tenantAcme, Set.of(), Set.of(RoleCodes.HRLAB_SUPER_ADMIN),
                Set.of(PermissionCodes.PROJECT_READ), Set.of(), false, "ru-RU"));
        try {
            assertThatThrownBy(() -> findProjectQuery.findById(projectBeta.getId()))
                    .as("ACME caller probing Beta project UUID must hit the canonical "
                            + "TenantAccessDeniedException (translated to HTTP 404)")
                    .isInstanceOf(TenantAccessDeniedException.class);
        } finally {
            TenantContextHolder.clear();
        }

        // Sanity: the legitimate Beta tenant CAN still load its own project.
        TenantContextHolder.set(new TenantContext(
                userId, tenantBeta, Set.of(), Set.of(RoleCodes.HRLAB_SUPER_ADMIN),
                Set.of(PermissionCodes.PROJECT_READ), Set.of(), false, "ru-RU"));
        try {
            assertThat(findProjectQuery.findById(projectBeta.getId()).id())
                    .isEqualTo(projectBeta.getId());
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * TI-Reg-04 (cross-locale + cross-tenant isolation). ACME persists a
     * project with {@code name_i18n.ru-RU = "Проект"} and {@code uz-Latn-UZ =
     * "Loyiha"}. A Beta-tenant caller — independent of the active locale —
     * must not see, probe, or page over this row. Localization stores
     * translations on the same row, so a stale or mis-routed locale must
     * never bypass the tenant predicate.
     */
    @Test
    void crossLocaleProjectFromTenantBIsInvisibleToTenantA() {
        UUID tenantAcme = newSeededTenantId();
        UUID tenantBeta = newSeededTenantId();
        ProjectJpaEntity acmeProject = projects.save(new ProjectJpaEntity(
                UUID.randomUUID(), tenantAcme, "PRJ-ACME-001",
                Map.of(
                        "ru-RU", "Проект Альфа",
                        "uz-Latn-UZ", "Loyiha Alfa",
                        "uz-Cyrl-UZ", "Лойиҳа Алфа",
                        "en-US", "Alpha Project"),
                "Multilingual project owned by ACME",
                ProjectStatus.ACTIVE, null, null, null));

        // Direct id probe from Beta — empty regardless of which locale label
        // the caller "knows" about.
        Optional<ProjectJpaEntity> probe =
                projects.findByIdAndTenantId(acmeProject.getId(), tenantBeta);
        assertThat(probe).isEmpty();

        // Pagination from Beta excluding ARCHIVED — must NOT include ACME row,
        // even though it carries a Latin-script Uzbek label Beta might match
        // in client-side search.
        Page<ProjectJpaEntity> betaPage = projects.findAllByTenantIdAndStatusNot(
                tenantBeta, ProjectStatus.ARCHIVED, PageRequest.of(0, 50));
        assertThat(betaPage.getContent())
                .as("Beta paging must not surface ACME's multilingual project")
                .extracting(ProjectJpaEntity::getId)
                .doesNotContain(acmeProject.getId());

        // Confirm ACME itself still sees it (translations preserved).
        Optional<ProjectJpaEntity> ownerProbe =
                projects.findByIdAndTenantId(acmeProject.getId(), tenantAcme);
        assertThat(ownerProbe).isPresent();
        assertThat(ownerProbe.get().getNameI18n())
                .containsKeys("ru-RU", "uz-Latn-UZ", "uz-Cyrl-UZ", "en-US");
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
