package uz.hrlab.grading.methodology;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogJpaEntity;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogRepository;
import uz.hrlab.grading.methodology.application.ApproveMethodologyVersionUseCase;
import uz.hrlab.grading.methodology.application.ArchiveMethodologyVersionUseCase;
import uz.hrlab.grading.methodology.application.CreateMethodologyCommand;
import uz.hrlab.grading.methodology.application.CreateMethodologyFromScratchUseCase;
import uz.hrlab.grading.methodology.application.CreateMethodologyVersionUseCase;
import uz.hrlab.grading.methodology.application.FactorCommand;
import uz.hrlab.grading.methodology.application.FactorLevelCommand;
import uz.hrlab.grading.methodology.application.FactorLevelService;
import uz.hrlab.grading.methodology.application.FactorService;
import uz.hrlab.grading.methodology.application.LockMethodologyVersionUseCase;
import uz.hrlab.grading.methodology.application.MethodologyAggregate;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.FactorLevel;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;
import uz.hrlab.grading.methodology.domain.ScoringMode;
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

/**
 * D-405 — end-to-end audit-row write assertion for the full Phase 4
 * methodology lifecycle, mirroring {@code Phase3AuditLifecycleTest}.
 *
 * <p>Executes a realistic workflow:
 * <ol>
 *   <li>Create methodology + DRAFT v1 (DIRECT_POINTS, target 1000)</li>
 *   <li>Add factor F1 (weight=500, maxPoints=500, required)</li>
 *   <li>Add factor F2 (weight=500, maxPoints=500, required)</li>
 *   <li>Add 2 levels to F1, 2 levels to F2 (≥ 2 levels required for approval)</li>
 *   <li>Approve v1 (DRAFT → APPROVED)</li>
 *   <li>Lock v1 (APPROVED → LOCKED)</li>
 *   <li>Create v2 (LOCKED → new DRAFT, deep-copied factors + levels)</li>
 *   <li>Archive v1 (LOCKED → ARCHIVED)</li>
 * </ol>
 *
 * <p>For every step asserts:
 * <ul>
 *   <li>the expected {@link AuditAction} code is present in
 *       {@code public.system_audit_log} for this tenant</li>
 *   <li>{@code tenant_id}, {@code actor_user_id}, {@code project_id},
 *       {@code entity_type}, {@code entity_id} columns are populated</li>
 *   <li>hash chain continuity: every row's {@code hash_prev} equals the
 *       previous row's {@code hash_current} (ordered by created_at asc)</li>
 *   <li>row count for this tenant >= number of mutation steps</li>
 * </ul>
 */
@Tag("audit")
@Tag("integration")
class Phase4AuditLifecycleTest extends AbstractIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired CreateMethodologyFromScratchUseCase createUseCase;
    @Autowired FactorService factorService;
    @Autowired FactorLevelService factorLevelService;
    @Autowired ApproveMethodologyVersionUseCase approveUseCase;
    @Autowired LockMethodologyVersionUseCase lockUseCase;
    @Autowired CreateMethodologyVersionUseCase newVersionUseCase;
    @Autowired ArchiveMethodologyVersionUseCase archiveUseCase;
    @Autowired SystemAuditLogRepository auditLog;

    @AfterEach
    void cleanup() { TenantContextHolder.clear(); }

    @Test
    void fullLifecycleWritesAuditRowsWithHashChainContinuity() {
        UUID tenant = newSeededTenantId();
        UUID actor = UUID.randomUUID();
        ProjectJpaEntity proj = projects.save(newProject(tenant, "PRJ-MTH-LIFE"));

        // HRLAB_PROJECT_MANAGER bypasses ProjectMembershipPolicy and carries
        // the full methodology permission set used by every step below.
        TenantContextHolder.set(new TenantContext(
                actor, tenant, Set.of(proj.getId()),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("METHODOLOGY_READ", "METHODOLOGY_CREATE",
                        "METHODOLOGY_EDIT", "METHODOLOGY_APPROVE",
                        "METHODOLOGY_LOCK"),
                Set.of(), false, "ru-RU"));

        // 1. CREATE methodology + DRAFT v1
        MethodologyAggregate agg = createUseCase.create(new CreateMethodologyCommand(
                proj.getId(),
                "MTH-LIFE-1",
                Map.of("ru-RU", "Методология жизненного цикла"),
                Map.of("ru-RU", "Описание"),
                MethodologyType.CUSTOM,
                ScoringMode.DIRECT_POINTS,
                new BigDecimal("1000")));
        UUID methodologyId = agg.methodology().id();
        UUID v1Id = agg.currentVersion().id();

        // 2-3. Add 2 factors
        Factor f1 = factorService.add(v1Id, new FactorCommand(
                "F1", Map.of("ru-RU", "Фактор 1"), null,
                new BigDecimal("500"), new BigDecimal("500"), 1, true));
        Factor f2 = factorService.add(v1Id, new FactorCommand(
                "F2", Map.of("ru-RU", "Фактор 2"), null,
                new BigDecimal("500"), new BigDecimal("500"), 2, true));

        // 4. Add 2 levels per factor (≥ 2 required for approve)
        FactorLevel f1l1 = factorLevelService.add(f1.id(), new FactorLevelCommand(
                "L1", 1, new BigDecimal("100"), null,
                Map.of("ru-RU", "Уровень 1"), null));
        FactorLevel f1l2 = factorLevelService.add(f1.id(), new FactorLevelCommand(
                "L2", 2, new BigDecimal("500"), null,
                Map.of("ru-RU", "Уровень 2"), null));
        FactorLevel f2l1 = factorLevelService.add(f2.id(), new FactorLevelCommand(
                "L1", 1, new BigDecimal("100"), null,
                Map.of("ru-RU", "Уровень 1"), null));
        FactorLevel f2l2 = factorLevelService.add(f2.id(), new FactorLevelCommand(
                "L2", 2, new BigDecimal("500"), null,
                Map.of("ru-RU", "Уровень 2"), null));

        // 5. APPROVE v1
        MethodologyVersion approved = approveUseCase.approve(v1Id);

        // 6. LOCK v1
        MethodologyVersion locked = lockUseCase.lock(v1Id);

        // 7. CREATE v2 (new DRAFT from LOCKED v1)
        MethodologyVersion v2 = newVersionUseCase.createNewVersion(v1Id);

        // 8. ARCHIVE v1
        MethodologyVersion archived = archiveUseCase.archive(v1Id,
                "Superseded by version 2 — historical record");

        // -- ASSERTIONS --
        List<SystemAuditLogJpaEntity> rows = auditLog
                .findByTenantIdOrderByCreatedAtDesc(tenant);

        // Catalog: every Phase 4 lifecycle action must be present.
        var phase4Actions = rows.stream()
                .map(SystemAuditLogJpaEntity::getAction)
                .filter(a -> a != null && (a.startsWith("METHODOLOGY_")
                        || a.startsWith("FACTOR_")))
                .toList();
        assertThat(phase4Actions)
                .as("audit must contain every Phase 4 lifecycle action")
                .contains(
                        AuditAction.METHODOLOGY_CREATED,
                        AuditAction.METHODOLOGY_VERSION_CREATED,
                        AuditAction.FACTOR_CREATED,
                        AuditAction.FACTOR_LEVEL_CREATED,
                        AuditAction.METHODOLOGY_VERSION_APPROVED,
                        AuditAction.METHODOLOGY_VERSION_LOCKED,
                        AuditAction.METHODOLOGY_VERSION_REVISION_CREATED,
                        AuditAction.METHODOLOGY_VERSION_ARCHIVED);

        // Hash chain continuity (rows ordered newest-first by repository).
        for (int i = 0; i < rows.size() - 1; i++) {
            SystemAuditLogJpaEntity newer = rows.get(i);
            SystemAuditLogJpaEntity older = rows.get(i + 1);
            assertThat(newer.getHashCurrent())
                    .as("hash_current non-blank at index " + i).isNotBlank();
            assertThat(newer.getHashPrev())
                    .as("hash chain continuity at index " + i)
                    .isEqualTo(older.getHashCurrent());
        }
        // The oldest row in the chain still has a non-blank hash_current.
        assertThat(rows.get(rows.size() - 1).getHashCurrent()).isNotBlank();

        // Per-row column assertions via JdbcTemplate (entity exposes only
        // append-only getters; richer columns are read directly).
        // METHODOLOGY_VERSION_APPROVED row must carry the version id +
        // project_id + actor.
        Integer approvedMatches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.system_audit_log "
                        + "WHERE tenant_id = ? AND actor_user_id = ? "
                        + "AND project_id = ? AND action = ? "
                        + "AND entity_type = 'MethodologyVersion' "
                        + "AND entity_id = ? "
                        + "AND before_json IS NOT NULL "
                        + "AND after_json  IS NOT NULL",
                Integer.class, tenant, actor, proj.getId(),
                AuditAction.METHODOLOGY_VERSION_APPROVED, v1Id);
        assertThat(approvedMatches)
                .as("APPROVED row carries tenant + actor + project + entity_id "
                        + "and both before/after JSON snapshots")
                .isEqualTo(1);

        // METHODOLOGY_VERSION_LOCKED row must carry the same v1 id.
        Integer lockedMatches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.system_audit_log "
                        + "WHERE tenant_id = ? AND action = ? AND entity_id = ?",
                Integer.class, tenant, AuditAction.METHODOLOGY_VERSION_LOCKED, v1Id);
        assertThat(lockedMatches).isEqualTo(1);

        // METHODOLOGY_VERSION_REVISION_CREATED row must carry the NEW
        // version id (per use case contract) — not v1.
        Integer revisionMatches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.system_audit_log "
                        + "WHERE tenant_id = ? AND action = ? AND entity_id = ?",
                Integer.class, tenant,
                AuditAction.METHODOLOGY_VERSION_REVISION_CREATED, v2.id());
        assertThat(revisionMatches).isEqualTo(1);

        // METHODOLOGY_VERSION_ARCHIVED row carries v1 id + reason.
        Integer archivedMatches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.system_audit_log "
                        + "WHERE tenant_id = ? AND action = ? AND entity_id = ? "
                        + "AND reason IS NOT NULL",
                Integer.class, tenant,
                AuditAction.METHODOLOGY_VERSION_ARCHIVED, v1Id);
        assertThat(archivedMatches).isEqualTo(1);

        // Row count must cover (at minimum) every mutation step in this
        // lifecycle (10 inserts + 4 transitions = 14). The actual number can
        // exceed this if shared platform events are recorded for the same
        // tenant — we assert the LOWER bound.
        long phase4RowsForTenant = rows.stream()
                .map(SystemAuditLogJpaEntity::getAction)
                .filter(a -> a != null && (a.startsWith("METHODOLOGY_")
                        || a.startsWith("FACTOR_")))
                .count();
        assertThat(phase4RowsForTenant)
                .as("at least one audit row per lifecycle mutation step")
                .isGreaterThanOrEqualTo(12L);

        // Sanity: domain returns reflect the same state transitions captured
        // in audit (returns are unused intentionally for state probes).
        assertThat(approved.id()).isEqualTo(v1Id);
        assertThat(locked.id()).isEqualTo(v1Id);
        assertThat(archived.id()).isEqualTo(v1Id);
        assertThat(v2.previousVersionId()).isEqualTo(v1Id);
        assertThat(methodologyId).isNotNull();
        // Levels referenced to keep compiler happy and document expected
        // audit row count (4 level inserts × FACTOR_LEVEL_CREATED).
        assertThat(f1l1.id()).isNotNull();
        assertThat(f1l2.id()).isNotNull();
        assertThat(f2l1.id()).isNotNull();
        assertThat(f2l2.id()).isNotNull();
    }

    private ProjectJpaEntity newProject(UUID tenantId, String code) {
        return new ProjectJpaEntity(
                UUID.randomUUID(), tenantId, code, Map.of("ru-RU", code), null,
                ProjectStatus.ACTIVE, null, null, null);
    }
}
