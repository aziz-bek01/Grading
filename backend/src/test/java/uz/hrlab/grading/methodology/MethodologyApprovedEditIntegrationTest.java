package uz.hrlab.grading.methodology;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.audit.application.AuditAction;
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
    @Autowired PositionRepository positions;
    @Autowired CreateMethodologyFromScratchUseCase createUseCase;
    @Autowired FactorService factorService;
    @Autowired FactorLevelService factorLevelService;
    @Autowired ApproveMethodologyVersionUseCase approveUseCase;
    @Autowired CreateEvaluationUseCase createEvaluation;
    @Autowired UpsertEvaluationScoreUseCase upsertScore;

    private UUID tenant;
    private UUID actor;
    private ProjectJpaEntity proj;

    @AfterEach
    void cleanup() { TenantContextHolder.clear(); }

    private void asSuperAdmin() {
        TenantContextHolder.set(new TenantContext(
                actor, tenant, Set.of(proj.getId()),
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
        PositionJpaEntity p = new PositionJpaEntity(
                UUID.randomUUID(), tenant, proj.getId(), null,
                "POS-" + UUID.randomUUID().toString().substring(0, 8),
                Map.of("ru-RU", "Должность"), null, null, null, null, PositionStatus.ACTIVE);
        return positions.save(p).getId();
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

    private ProjectJpaEntity newProject(UUID tenantId, String code) {
        return new ProjectJpaEntity(
                UUID.randomUUID(), tenantId, code, Map.of("ru-RU", code), null,
                ProjectStatus.ACTIVE, null, null, null);
    }
}
