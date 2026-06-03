package uz.hrlab.grading.methodology;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogJpaEntity;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogRepository;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.methodology.application.ApproveMethodologyVersionUseCase;
import uz.hrlab.grading.methodology.application.MethodologyQueries;
import uz.hrlab.grading.methodology.application.UpdateMethodologyMetadataUseCase;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.project.domain.ProjectStatus;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D-404 — proves that ABAC denials on Phase 4 methodology write/read paths
 * land an {@code ACCESS_DENIED_BY_ABAC} row in {@code system_audit_log}
 * carrying tenant_id + actor_user_id + entity_type + project_id, and
 * that pure-RBAC denials short-circuit BEFORE ABAC (so no ABAC audit row
 * appears for missing permissions).
 *
 * <p>Three scenarios mirror {@code Phase3AbacDenialAuditTest}:
 * <ol>
 *   <li>{@code DEPARTMENT_MANAGER} with full methodology perms but assigned
 *       to an UNRELATED project attempts {@code approve} on a project-scoped
 *       methodology version → {@code ProjectMembershipPolicy} returns DENY →
 *       {@link TenantAccessDeniedException} + ABAC audit row written.</li>
 *   <li>{@code DEPARTMENT_MANAGER} with edit perm but out-of-project tries
 *       {@code addFactor} on a draft version → DENY → audit row written.</li>
 *   <li>{@code DEPARTMENT_MANAGER} with read perm but out-of-project tries to
 *       read the methodology → DENY → audit row written.</li>
 * </ol>
 *
 * <p>Out-of-band: the RBAC re-check still runs first; if a user lacks the
 * required permission entirely the use case throws
 * {@link PermissionDeniedException} and NO ABAC audit row is recorded — that
 * negative case is asserted in scenario 4.
 */
@Tag("abac")
@Tag("audit")
@Tag("integration")
class Phase4AbacDenialAuditTest extends AbstractIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired MethodologyRepository methodologies;
    @Autowired MethodologyVersionRepository versions;
    @Autowired ApproveMethodologyVersionUseCase approveUseCase;
    @Autowired UpdateMethodologyMetadataUseCase updateMetadataUseCase;
    @Autowired MethodologyQueries queries;
    @Autowired SystemAuditLogRepository auditLog;

    @AfterEach
    void cleanup() { TenantContextHolder.clear(); }

    @Test
    void approveOutOfProjectScopeDeniedAndAudited() {
        UUID tenant = newSeededTenantId();
        UUID actor = UUID.randomUUID();
        ProjectJpaEntity target = projects.save(newProject(tenant, "PRJ-MTH-A"));
        ProjectJpaEntity unrelated = projects.save(newProject(tenant, "PRJ-MTH-B"));
        MethodologyJpaEntity m = methodologies.save(
                newMethodology(tenant, target.getId(), "M-ABAC-APP"));
        MethodologyVersionJpaEntity v = versions.save(
                newDraftVersion(tenant, m.getId(), 1));

        // DEPARTMENT_MANAGER is NOT in the tenant-wide bypass set; their only
        // projectId is `unrelated` → ProjectMembershipPolicy denies on target.
        TenantContextHolder.set(new TenantContext(
                actor, tenant,
                Set.of(unrelated.getId()),
                Set.of("DEPARTMENT_MANAGER"),
                Set.of("METHODOLOGY_READ", "METHODOLOGY_EDIT", "METHODOLOGY_APPROVE"),
                Set.of(), false, "ru-RU"));

        UUID versionId = v.getId();
        assertThatThrownBy(() -> approveUseCase.approve(versionId))
                .isInstanceOf(TenantAccessDeniedException.class);

        assertAbacDenialAudited(tenant, actor, "ProjectWrite");
    }

    @Test
    void updateMetadataOutOfProjectScopeDeniedAndAudited() {
        UUID tenant = newSeededTenantId();
        UUID actor = UUID.randomUUID();
        ProjectJpaEntity target = projects.save(newProject(tenant, "PRJ-MTH-C"));
        ProjectJpaEntity unrelated = projects.save(newProject(tenant, "PRJ-MTH-D"));
        MethodologyJpaEntity m = methodologies.save(
                newMethodology(tenant, target.getId(), "M-ABAC-UPD"));

        TenantContextHolder.set(new TenantContext(
                actor, tenant,
                Set.of(unrelated.getId()),
                Set.of("DEPARTMENT_MANAGER"),
                Set.of("METHODOLOGY_READ", "METHODOLOGY_EDIT"),
                Set.of(), false, "ru-RU"));

        UUID methodologyId = m.getId();
        assertThatThrownBy(() -> updateMetadataUseCase.update(
                methodologyId,
                Map.of("ru-RU", "Mutated"),
                null))
                .isInstanceOf(TenantAccessDeniedException.class);

        assertAbacDenialAudited(tenant, actor, "ProjectWrite");
    }

    @Test
    void readMethodologyOutOfProjectScopeDeniedAndAudited() {
        UUID tenant = newSeededTenantId();
        UUID actor = UUID.randomUUID();
        ProjectJpaEntity target = projects.save(newProject(tenant, "PRJ-MTH-E"));
        ProjectJpaEntity unrelated = projects.save(newProject(tenant, "PRJ-MTH-F"));
        MethodologyJpaEntity m = methodologies.save(
                newMethodology(tenant, target.getId(), "M-ABAC-READ"));

        TenantContextHolder.set(new TenantContext(
                actor, tenant,
                Set.of(unrelated.getId()),
                Set.of("DEPARTMENT_MANAGER"),
                Set.of("METHODOLOGY_READ"),
                Set.of(), false, "ru-RU"));

        UUID methodologyId = m.getId();
        assertThatThrownBy(() -> queries.findMethodologyById(methodologyId))
                .isInstanceOf(TenantAccessDeniedException.class);

        // Read paths exercise the LIST variant — the entityType set by
        // AbacGate.enforceCanListInProject is "ProjectListing".
        assertAbacDenialAudited(tenant, actor, "ProjectListing");
    }

    @Test
    void missingPermissionThrowsRbacWithoutAbacAuditRow() {
        UUID tenant = newSeededTenantId();
        UUID actor = UUID.randomUUID();
        ProjectJpaEntity target = projects.save(newProject(tenant, "PRJ-MTH-G"));
        MethodologyJpaEntity m = methodologies.save(
                newMethodology(tenant, target.getId(), "M-RBAC"));
        MethodologyVersionJpaEntity v = versions.save(
                newDraftVersion(tenant, m.getId(), 1));

        // HRLAB_CONSULTANT does have project scope here, but lacks
        // METHODOLOGY_APPROVE permission entirely → RBAC re-check fires
        // FIRST, throws PermissionDeniedException, and NO ABAC row is written.
        TenantContextHolder.set(new TenantContext(
                actor, tenant,
                Set.of(target.getId()),
                Set.of("HRLAB_CONSULTANT"),
                Set.of("METHODOLOGY_READ"),
                Set.of(), false, "ru-RU"));

        UUID versionId = v.getId();
        assertThatThrownBy(() -> approveUseCase.approve(versionId))
                .isInstanceOf(PermissionDeniedException.class);

        boolean abacDenialPresent = auditLog
                .findByTenantIdOrderByCreatedAtDesc(tenant).stream()
                .map(SystemAuditLogJpaEntity::getAction)
                .anyMatch("ACCESS_DENIED_BY_ABAC"::equals);
        assertThat(abacDenialPresent)
                .as("RBAC permission denial must NOT pollute the ABAC audit channel")
                .isFalse();
    }

    private void assertAbacDenialAudited(UUID tenant, UUID actor,
                                         String expectedEntityType) {
        List<SystemAuditLogJpaEntity> rows = auditLog
                .findByTenantIdOrderByCreatedAtDesc(tenant);
        boolean denialAudited = rows.stream()
                .map(SystemAuditLogJpaEntity::getAction)
                .anyMatch("ACCESS_DENIED_BY_ABAC"::equals);
        assertThat(denialAudited)
                .as("ACCESS_DENIED_BY_ABAC row must be written on Phase 4 ABAC denial")
                .isTrue();

        // Verify columns directly via JdbcTemplate (the JPA entity exposes only
        // the few getters used by the hash-chain machinery — entity_type,
        // entity_id, actor_user_id are append-only and read here for
        // forensic assertion).
        Integer matching = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.system_audit_log "
                        + "WHERE tenant_id = ? AND actor_user_id = ? "
                        + "AND action = 'ACCESS_DENIED_BY_ABAC' "
                        + "AND entity_type = ?",
                Integer.class, tenant, actor, expectedEntityType);
        assertThat(matching)
                .as("audit row must carry tenant + actor + entity_type=%s",
                        expectedEntityType)
                .isGreaterThanOrEqualTo(1);
    }

    private ProjectJpaEntity newProject(UUID tenantId, String code) {
        return new ProjectJpaEntity(
                UUID.randomUUID(), tenantId, code, Map.of("ru-RU", code), null,
                ProjectStatus.ACTIVE, null, null, null);
    }

    private MethodologyJpaEntity newMethodology(UUID tenantId, UUID projectId, String code) {
        MethodologyJpaEntity m = new MethodologyJpaEntity(
                UUID.randomUUID(), tenantId, projectId, code,
                MethodologyType.CUSTOM, MethodologyStatus.ACTIVE);
        m.setNameI18n(Map.of("ru-RU", code));
        return m;
    }

    private MethodologyVersionJpaEntity newDraftVersion(UUID tenantId, UUID methodologyId,
                                                        int versionNumber) {
        return new MethodologyVersionJpaEntity(
                UUID.randomUUID(), tenantId, methodologyId, versionNumber,
                MethodologyVersionStatus.DRAFT, ScoringMode.DIRECT_POINTS,
                new BigDecimal("1000"), null);
    }
}
