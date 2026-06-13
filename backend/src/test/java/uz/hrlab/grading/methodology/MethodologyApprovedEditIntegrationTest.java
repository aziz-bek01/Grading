package uz.hrlab.grading.methodology;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.application.CreateEvaluationCommand;
import uz.hrlab.grading.evaluation.application.CreateEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.UpsertEvaluationScoreCommand;
import uz.hrlab.grading.evaluation.application.UpsertEvaluationScoreUseCase;
import uz.hrlab.grading.methodology.application.ApproveMethodologyVersionUseCase;
import uz.hrlab.grading.methodology.application.CreateMethodologyCommand;
import uz.hrlab.grading.methodology.application.CreateMethodologyFromScratchUseCase;
import uz.hrlab.grading.methodology.application.FactorCommand;
import uz.hrlab.grading.methodology.application.FactorLevelCommand;
import uz.hrlab.grading.methodology.application.FactorLevelService;
import uz.hrlab.grading.methodology.application.FactorService;
import uz.hrlab.grading.methodology.application.MethodologyAggregate;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.FactorLevel;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.MethodologyVersionTransitionRejectedException;
import uz.hrlab.grading.methodology.domain.ScoringMode;
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

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BE-2 / BE-4 / BE-5 end-to-end (Testcontainers, CI — needs Docker).
 *
 * <p>Proves the approved-methodology in-place edit: the DB trigger carve-out
 * (changelog 042) admits the write only when the service set the
 * {@code app.methodology_approved_edit} GUC on the super-admin branch; a plain
 * METHODOLOGY_EDIT holder is still rejected at the policy; a referenced level is
 * soft-deprecated; and exactly one {@code METHODOLOGY_APPROVED_EDIT} umbrella
 * audit row is written carrying the frozen-evaluation count.
 */
@Tag("integration")
class MethodologyApprovedEditIntegrationTest extends AbstractIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired DepartmentRepository departments;
    @Autowired PositionRepository positions;
    @Autowired CreateMethodologyFromScratchUseCase createUseCase;
    @Autowired FactorService factorService;
    @Autowired FactorLevelService factorLevelService;
    @Autowired ApproveMethodologyVersionUseCase approveUseCase;
    @Autowired CreateEvaluationUseCase createEvaluation;
    @Autowired UpsertEvaluationScoreUseCase upsertScore;
    @Autowired TransactionTemplate txTemplate;

    private UUID tenant;
    private UUID actor;
    private ProjectJpaEntity proj;
    /** Lazily created once per test (positions.department_id is NOT NULL). */
    private UUID departmentId;

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
        departmentId = null;
    }

    private void asSuperAdmin() {
        asSuperAdmin(actor, tenant, proj.getId());
    }

    /** Super-admin context scoped to an explicit tenant/project (F-4 cross-tenant). */
    private void asSuperAdmin(UUID actorId, UUID tenantId, UUID projectId) {
        TenantContextHolder.set(new TenantContext(
                actorId, tenantId, Set.of(projectId),
                Set.of("HRLAB_SUPER_ADMIN"),
                Set.of("METHODOLOGY_READ", "METHODOLOGY_CREATE", "METHODOLOGY_EDIT",
                        "METHODOLOGY_APPROVE", "METHODOLOGY_LOCK",
                        "METHODOLOGY_EDIT_APPROVED",
                        "EVALUATION_READ", "EVALUATION_EDIT"),
                Set.of(), false, "ru-RU"));
    }

    private void asPlainEditor() {
        TenantContextHolder.set(new TenantContext(
                actor, tenant, Set.of(proj.getId()),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("METHODOLOGY_READ", "METHODOLOGY_EDIT", "METHODOLOGY_APPROVE"),
                Set.of(), false, "ru-RU"));
    }

    /** Build a methodology with 1 factor + 2 levels and approve it. Returns ids. */
    private Fixture buildApprovedMethodology() {
        MethodologyAggregate agg = createUseCase.create(new CreateMethodologyCommand(
                proj.getId(), "MTH-AE-" + UUID.randomUUID().toString().substring(0, 8),
                Map.of("ru-RU", "Методология"), Map.of("ru-RU", "Описание"),
                MethodologyType.CUSTOM, ScoringMode.DIRECT_POINTS, new BigDecimal("1000")));
        UUID versionId = agg.currentVersion().id();
        Factor f = factorService.add(versionId, new FactorCommand(
                "F1", Map.of("ru-RU", "Фактор 1"), null,
                new BigDecimal("500"), new BigDecimal("500"), 1, true));
        FactorLevel l1 = factorLevelService.add(f.id(), new FactorLevelCommand(
                "L1", 1, new BigDecimal("100"), null, Map.of("ru-RU", "Уровень 1"), null));
        FactorLevel l2 = factorLevelService.add(f.id(), new FactorLevelCommand(
                "L2", 2, new BigDecimal("500"), null, Map.of("ru-RU", "Уровень 2"), null));
        approveUseCase.approve(versionId);
        return new Fixture(versionId, f.id(), l1.id(), l2.id());
    }

    private record Fixture(UUID versionId, UUID factorId, UUID levelId1, UUID levelId2) { }

    private UUID newPosition() {
        // positions.department_id is NOT NULL (FK to departments). Reuse the same
        // newDept(...) shape as the other Phase 2/5 integration tests; create one
        // department per test for the active tenant+project and thread its id in.
        if (departmentId == null) {
            departmentId = departments.save(newDept(tenant, proj.getId(),
                    "DPT-AE-" + UUID.randomUUID().toString().substring(0, 8))).getId();
        }
        PositionJpaEntity p = new PositionJpaEntity(
                UUID.randomUUID(), tenant, proj.getId(), departmentId,
                "POS-" + UUID.randomUUID().toString().substring(0, 8),
                Map.of("ru-RU", "Должность"), null, null, null, null, PositionStatus.ACTIVE);
        return positions.save(p).getId();
    }

    private DepartmentJpaEntity newDept(UUID tenantId, UUID projectId, String code) {
        return new DepartmentJpaEntity(
                UUID.randomUUID(), tenantId, projectId, null, code,
                Map.of("ru-RU", code), DepartmentType.DEPARTMENT, DepartmentStatus.ACTIVE);
    }

    @Test
    void superAdminCanEditFactorWeightOnApprovedVersion_dbTriggerAdmitsWrite() {
        tenant = newSeededTenantId();
        actor = UUID.randomUUID();
        proj = projects.save(newProject(tenant, "PRJ-AE-1"));
        asSuperAdmin();
        Fixture fx = buildApprovedMethodology();

        // Edit a scoring field on the APPROVED version — must succeed (GUC carve-out).
        Factor updated = factorService.update(fx.factorId(), new FactorCommand(
                null, null, null, new BigDecimal("600"), null, null, null));
        assertThat(updated.weight()).isEqualByComparingTo("600");

        // Exactly one umbrella event for this edit, carrying frozenEvaluationCount.
        Integer umbrella = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.system_audit_log "
                        + "WHERE tenant_id = ? AND action = ? AND entity_id = ? "
                        + "AND (after_json ->> 'frozenEvaluationCount') IS NOT NULL",
                Integer.class, tenant, AuditAction.METHODOLOGY_APPROVED_EDIT, fx.versionId());
        assertThat(umbrella).isEqualTo(1);
    }

    @Test
    void plainEditorCannotEditApprovedVersion() {
        tenant = newSeededTenantId();
        actor = UUID.randomUUID();
        proj = projects.save(newProject(tenant, "PRJ-AE-2"));
        asSuperAdmin();
        Fixture fx = buildApprovedMethodology();

        asPlainEditor();
        assertThatThrownBy(() -> factorService.update(fx.factorId(), new FactorCommand(
                null, null, null, new BigDecimal("600"), null, null, null)))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
    }

    @Test
    void umbrellaFrozenCountMatchesPinnedEvaluations() {
        tenant = newSeededTenantId();
        actor = UUID.randomUUID();
        proj = projects.save(newProject(tenant, "PRJ-AE-3"));
        asSuperAdmin();
        Fixture fx = buildApprovedMethodology();

        // Pin 2 evaluations to the approved version.
        UUID pos1 = newPosition();
        UUID pos2 = newPosition();
        createEvaluation.create(new CreateEvaluationCommand(pos1, fx.versionId(), actor, null, null));
        createEvaluation.create(new CreateEvaluationCommand(pos2, fx.versionId(), actor, null, null));

        factorService.update(fx.factorId(), new FactorCommand(
                null, null, null, new BigDecimal("600"), null, null, null));

        Long frozen = jdbcTemplate.queryForObject(
                "SELECT (after_json ->> 'frozenEvaluationCount')::bigint "
                        + "FROM public.system_audit_log "
                        + "WHERE tenant_id = ? AND action = ? AND entity_id = ? "
                        + "ORDER BY created_at DESC LIMIT 1",
                Long.class, tenant, AuditAction.METHODOLOGY_APPROVED_EDIT, fx.versionId());
        assertThat(frozen).isEqualTo(2L);
    }

    @Test
    void referencedLevelOnApprovedIsSoftDeprecated_scoreUntouched() {
        tenant = newSeededTenantId();
        actor = UUID.randomUUID();
        proj = projects.save(newProject(tenant, "PRJ-AE-4"));
        asSuperAdmin();
        Fixture fx = buildApprovedMethodology();

        // Score an evaluation against level 1 so the level is referenced.
        UUID pos = newPosition();
        var eval = createEvaluation.create(
                new CreateEvaluationCommand(pos, fx.versionId(), actor, null, null));
        upsertScore.upsert(new UpsertEvaluationScoreCommand(
                eval.id(), fx.factorId(), fx.levelId1(), "initial"));

        // Remove the referenced level — must SOFT-deprecate, not delete.
        factorLevelService.remove(fx.levelId1());

        Integer stillPresent = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM factor_levels WHERE id = ? AND deprecated_at IS NOT NULL",
                Integer.class, fx.levelId1());
        assertThat(stillPresent).isEqualTo(1);

        // The score row that referenced the level is untouched.
        Integer scoreRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM evaluation_scores WHERE factor_level_id = ?",
                Integer.class, fx.levelId1());
        assertThat(scoreRows).isEqualTo(1);

        Integer deprecateAudit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.system_audit_log "
                        + "WHERE tenant_id = ? AND action = ? AND entity_id = ?",
                Integer.class, tenant, AuditAction.FACTOR_LEVEL_DEPRECATED, fx.levelId1());
        assertThat(deprecateAudit).isEqualTo(1);
    }

    @Test
    void unreferencedLevelOnApprovedIsHardDeleted() {
        tenant = newSeededTenantId();
        actor = UUID.randomUUID();
        proj = projects.save(newProject(tenant, "PRJ-AE-5"));
        asSuperAdmin();
        Fixture fx = buildApprovedMethodology();

        // level 2 is unreferenced — hard delete.
        factorLevelService.remove(fx.levelId2());

        Integer present = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM factor_levels WHERE id = ?",
                Integer.class, fx.levelId2());
        assertThat(present).isZero();
    }

    // ------------------------------------------------------------------
    // QA-1 — headline acceptance: byte-for-byte preservation of a
    // pre-existing evaluation's stored totals + per-factor scores when a
    // super admin edits an APPROVED version (weight change + level add +
    // soft-deprecate of a referenced level). No recompute, no orphan.
    // ------------------------------------------------------------------

    @Test
    void approvedEdit_preservesPreExistingEvaluationScoresByteForByte() {
        tenant = newSeededTenantId();
        actor = UUID.randomUUID();
        proj = projects.save(newProject(tenant, "PRJ-AE-QA1"));
        asSuperAdmin();
        Fixture fx = buildApprovedMethodology();

        // A scored evaluation pinned to the version BEFORE the approved edit.
        UUID pos = newPosition();
        var eval = createEvaluation.create(
                new CreateEvaluationCommand(pos, fx.versionId(), actor, null, null));
        upsertScore.upsert(new UpsertEvaluationScoreCommand(
                eval.id(), fx.factorId(), fx.levelId1(), "initial"));

        // Capture the stored totals + per-factor score as the byte-for-byte baseline.
        BigDecimal rawTotalBefore = jdbcTemplate.queryForObject(
                "SELECT raw_total_score FROM evaluations WHERE id = ?",
                BigDecimal.class, eval.id());
        BigDecimal displayedTotalBefore = jdbcTemplate.queryForObject(
                "SELECT displayed_total_score FROM evaluations WHERE id = ?",
                BigDecimal.class, eval.id());
        BigDecimal rawFactorBefore = jdbcTemplate.queryForObject(
                "SELECT raw_factor_score FROM evaluation_scores "
                        + "WHERE evaluation_id = ? AND factor_id = ?",
                BigDecimal.class, eval.id(), fx.factorId());
        assertThat(rawTotalBefore).isNotNull();
        assertThat(displayedTotalBefore).isNotNull();
        assertThat(rawFactorBefore).isNotNull();

        // Super-admin approved edits: change weight, add a level, soft-deprecate
        // the referenced level (the one the evaluation scored against).
        factorService.update(fx.factorId(), new FactorCommand(
                null, null, null, new BigDecimal("750"), null, null, null));
        factorLevelService.add(fx.factorId(), new FactorLevelCommand(
                "L3", 3, new BigDecimal("900"), null, Map.of("ru-RU", "Уровень 3"), null));
        factorLevelService.remove(fx.levelId1()); // referenced -> soft-deprecate

        // The pre-existing evaluation's stored totals + per-factor score are
        // byte-for-byte unchanged: no recompute was triggered by methodology writes.
        BigDecimal rawTotalAfter = jdbcTemplate.queryForObject(
                "SELECT raw_total_score FROM evaluations WHERE id = ?",
                BigDecimal.class, eval.id());
        BigDecimal displayedTotalAfter = jdbcTemplate.queryForObject(
                "SELECT displayed_total_score FROM evaluations WHERE id = ?",
                BigDecimal.class, eval.id());
        BigDecimal rawFactorAfter = jdbcTemplate.queryForObject(
                "SELECT raw_factor_score FROM evaluation_scores "
                        + "WHERE evaluation_id = ? AND factor_id = ?",
                BigDecimal.class, eval.id(), fx.factorId());

        // BigDecimal equals() is scale-sensitive — exactly what "byte-for-byte" means.
        assertThat(rawTotalAfter).isEqualTo(rawTotalBefore);
        assertThat(displayedTotalAfter).isEqualTo(displayedTotalBefore);
        assertThat(rawFactorAfter).isEqualTo(rawFactorBefore);

        // FKs intact: the soft-deprecated level still exists (RESTRICT, not deleted)
        // and the score row still points at it — no orphan.
        Integer levelStillThere = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM factor_levels WHERE id = ? AND deprecated_at IS NOT NULL",
                Integer.class, fx.levelId1());
        assertThat(levelStillThere).isEqualTo(1);
        Integer scoreFkIntact = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM evaluation_scores es "
                        + "JOIN factor_levels fl ON fl.id = es.factor_level_id "
                        + "WHERE es.evaluation_id = ? AND es.factor_level_id = ?",
                Integer.class, eval.id(), fx.levelId1());
        assertThat(scoreFkIntact).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // F-3 — DB-trigger defence-in-depth, tested in ISOLATION (no service GUC).
    // Raw jdbcTemplate writes bypass FactorService/FactorLevelService entirely,
    // proving the changelog-042 trigger is the backstop and is exactly GUC-gated.
    // ------------------------------------------------------------------

    @Test
    void dbTrigger_blocksRawApprovedFactorWrite_whenGucNotSet() {
        tenant = newSeededTenantId();
        actor = UUID.randomUUID();
        proj = projects.save(newProject(tenant, "PRJ-AE-F3A"));
        asSuperAdmin();
        Fixture fx = buildApprovedMethodology();

        // No GUC set: a raw UPDATE on an APPROVED factor must RAISE 23514 with the
        // APPROVED-specific protected message (not the LOCKED message; APPROVED is
        // protected-but-editable-via-the-audited-use-case per changelog 042).
        assertThatThrownBy(() -> txTemplate.executeWithoutResult(s ->
                jdbcTemplate.update(
                        "UPDATE factors SET weight = ? WHERE id = ?",
                        new BigDecimal("999"), fx.factorId())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("23514")
                .hasMessageContaining("METHODOLOGY_VERSION_APPROVED_PROTECTED");

        // Likewise a raw UPDATE on an APPROVED factor_level must RAISE 23514 with
        // the same APPROVED-protected message.
        assertThatThrownBy(() -> txTemplate.executeWithoutResult(s ->
                jdbcTemplate.update(
                        "UPDATE factor_levels SET points = ? WHERE id = ?",
                        new BigDecimal("999"), fx.levelId1())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("23514")
                .hasMessageContaining("METHODOLOGY_VERSION_APPROVED_PROTECTED");

        // Nothing changed (transactions rolled back on the RAISE).
        BigDecimal weight = jdbcTemplate.queryForObject(
                "SELECT weight FROM factors WHERE id = ?", BigDecimal.class, fx.factorId());
        assertThat(weight).isEqualByComparingTo("500");
    }

    @Test
    void dbTrigger_admitsRawApprovedFactorWrite_whenGucSetOn() {
        tenant = newSeededTenantId();
        actor = UUID.randomUUID();
        proj = projects.save(newProject(tenant, "PRJ-AE-F3B"));
        asSuperAdmin();
        Fixture fx = buildApprovedMethodology();

        // With the GUC explicitly SET LOCAL ... = 'on', the same raw UPDATE on an
        // APPROVED factor succeeds — the carve-out is exactly GUC-gated.
        txTemplate.executeWithoutResult(s -> {
            jdbcTemplate.execute("SET LOCAL app.methodology_approved_edit = 'on'");
            int rows = jdbcTemplate.update(
                    "UPDATE factors SET weight = ? WHERE id = ?",
                    new BigDecimal("888"), fx.factorId());
            assertThat(rows).isEqualTo(1);
        });

        BigDecimal weight = jdbcTemplate.queryForObject(
                "SELECT weight FROM factors WHERE id = ?", BigDecimal.class, fx.factorId());
        assertThat(weight).isEqualByComparingTo("888");

        // And on factor_levels too.
        txTemplate.executeWithoutResult(s -> {
            jdbcTemplate.execute("SET LOCAL app.methodology_approved_edit = 'on'");
            int rows = jdbcTemplate.update(
                    "UPDATE factor_levels SET points = ? WHERE id = ?",
                    new BigDecimal("777"), fx.levelId1());
            assertThat(rows).isEqualTo(1);
        });
        BigDecimal points = jdbcTemplate.queryForObject(
                "SELECT points FROM factor_levels WHERE id = ?", BigDecimal.class, fx.levelId1());
        assertThat(points).isEqualByComparingTo("777");
    }

    @Test
    void dbTrigger_blocksRawLockedAndArchivedWrites_evenWithGucOn() {
        tenant = newSeededTenantId();
        actor = UUID.randomUUID();
        proj = projects.save(newProject(tenant, "PRJ-AE-F3C"));
        asSuperAdmin();
        Fixture fx = buildApprovedMethodology();

        // LOCKED: even with the GUC = 'on', the trigger RAISEs 23514
        // (LOCKED is unconditionally immutable; the carve-out never weakens it).
        forceVersionStatus(fx.versionId(), "LOCKED");
        assertThatThrownBy(() -> txTemplate.executeWithoutResult(s -> {
            jdbcTemplate.execute("SET LOCAL app.methodology_approved_edit = 'on'");
            jdbcTemplate.update("UPDATE factors SET weight = ? WHERE id = ?",
                    new BigDecimal("111"), fx.factorId());
        }))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("23514");

        // ARCHIVED: same — hard immutable regardless of GUC.
        forceVersionStatus(fx.versionId(), "ARCHIVED");
        assertThatThrownBy(() -> txTemplate.executeWithoutResult(s -> {
            jdbcTemplate.execute("SET LOCAL app.methodology_approved_edit = 'on'");
            jdbcTemplate.update("UPDATE factor_levels SET points = ? WHERE id = ?",
                    new BigDecimal("111"), fx.levelId1());
        }))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("23514");
    }

    /**
     * F-3 helper: flip a version's status by raw SQL so we can probe the trigger
     * for LOCKED / ARCHIVED in isolation. The status column itself carries no
     * factor/level immutability trigger, so this is a clean fixture mutation that
     * does not depend on the service transition use cases.
     */
    private void forceVersionStatus(UUID versionId, String status) {
        jdbcTemplate.update(
                "UPDATE methodology_versions SET status = ? WHERE id = ?",
                status, versionId);
    }

    // ------------------------------------------------------------------
    // F-4 — cross-tenant negative test on the highest-privilege path.
    // A super admin holding METHODOLOGY_EDIT_APPROVED in tenant A cannot reach
    // tenant B's APPROVED factor: the tenant-scoped load fails BEFORE the
    // approved branch, so no GUC, no umbrella audit, no mutation.
    // ------------------------------------------------------------------

    @Test
    void superAdminInTenantA_cannotEditApprovedFactorOfTenantB() {
        // Tenant B owns the APPROVED methodology + factor + level.
        UUID tenantB = newSeededTenantId();
        UUID actorB = UUID.randomUUID();
        ProjectJpaEntity projB = projects.save(newProject(tenantB, "PRJ-AE-F4B"));
        asSuperAdmin(actorB, tenantB, projB.getId());
        // buildApprovedMethodology builds under the active context; point fixture
        // fields at tenant B for the duration of the build.
        tenant = tenantB;
        actor = actorB;
        proj = projB;
        Fixture fxB = buildApprovedMethodology();

        BigDecimal weightBefore = jdbcTemplate.queryForObject(
                "SELECT weight FROM factors WHERE id = ?", BigDecimal.class, fxB.factorId());
        BigDecimal pointsBefore = jdbcTemplate.queryForObject(
                "SELECT points FROM factor_levels WHERE id = ?", BigDecimal.class, fxB.levelId1());

        // Now a DIFFERENT super admin, scoped to tenant A, attacks tenant B's ids.
        UUID tenantA = newSeededTenantId();
        UUID actorA = UUID.randomUUID();
        ProjectJpaEntity projA = projects.save(newProject(tenantA, "PRJ-AE-F4A"));
        asSuperAdmin(actorA, tenantA, projA.getId());

        // update / deleteFactor / level edit all fail at the tenant-scoped load
        // (findByIdAndTenantId) BEFORE the approved-edit branch.
        assertThatThrownBy(() -> factorService.update(fxB.factorId(), new FactorCommand(
                null, null, null, new BigDecimal("123"), null, null, null)))
                .isInstanceOf(TenantAccessDeniedException.class);
        assertThatThrownBy(() -> factorService.remove(fxB.factorId()))
                .isInstanceOf(TenantAccessDeniedException.class);
        assertThatThrownBy(() -> factorLevelService.update(fxB.levelId1(), new FactorLevelCommand(
                null, null, new BigDecimal("123"), null, null, null)))
                .isInstanceOf(TenantAccessDeniedException.class);

        // No side effects: tenant B's factor/level are byte-for-byte unchanged.
        BigDecimal weightAfter = jdbcTemplate.queryForObject(
                "SELECT weight FROM factors WHERE id = ?", BigDecimal.class, fxB.factorId());
        BigDecimal pointsAfter = jdbcTemplate.queryForObject(
                "SELECT points FROM factor_levels WHERE id = ?", BigDecimal.class, fxB.levelId1());
        assertThat(weightAfter).isEqualByComparingTo(weightBefore);
        assertThat(pointsAfter).isEqualByComparingTo(pointsBefore);

        // No METHODOLOGY_APPROVED_EDIT umbrella row was written for tenant A OR B
        // as a result of the rejected cross-tenant attempt.
        Integer umbrellaA = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.system_audit_log "
                        + "WHERE tenant_id = ? AND action = ?",
                Integer.class, tenantA, AuditAction.METHODOLOGY_APPROVED_EDIT);
        assertThat(umbrellaA).isZero();
        Integer umbrellaBForVersion = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.system_audit_log "
                        + "WHERE tenant_id = ? AND action = ? AND entity_id = ?",
                Integer.class, tenantB, AuditAction.METHODOLOGY_APPROVED_EDIT, fxB.versionId());
        assertThat(umbrellaBForVersion).isZero();
    }

    private ProjectJpaEntity newProject(UUID tenantId, String code) {
        return new ProjectJpaEntity(
                UUID.randomUUID(), tenantId, code, Map.of("ru-RU", code), null,
                ProjectStatus.ACTIVE, null, null, null);
    }
}
