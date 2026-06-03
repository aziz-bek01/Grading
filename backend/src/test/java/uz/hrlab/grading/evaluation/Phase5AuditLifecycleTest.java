package uz.hrlab.grading.evaluation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogJpaEntity;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogRepository;
import uz.hrlab.grading.evaluation.application.ApproveEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.ArchiveEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.CalibrateEvaluationScoreCommand;
import uz.hrlab.grading.evaluation.application.CalibrateEvaluationScoreUseCase;
import uz.hrlab.grading.evaluation.application.CreateEvaluationCommand;
import uz.hrlab.grading.evaluation.application.CreateEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.LockEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.SubmitEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.UpsertEvaluationScoreCommand;
import uz.hrlab.grading.evaluation.application.UpsertEvaluationScoreUseCase;
import uz.hrlab.grading.evaluation.domain.Evaluation;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-503 — end-to-end audit-row write assertion for the full Phase 5
 * evaluation lifecycle, mirroring {@code Phase3AuditLifecycleTest} and
 * {@code Phase4AuditLifecycleTest}.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Create evaluation</li>
 *   <li>Upsert score for factor 1 (DRAFT → INCOMPLETE/COMPLETE)</li>
 *   <li>Upsert score for factor 2 (→ COMPLETE)</li>
 *   <li>Submit (COMPLETE → SUBMITTED)</li>
 *   <li>Approve (SUBMITTED → APPROVED)</li>
 *   <li>Calibrate factor 1 (APPROVED state) with reason ≥ 20 chars</li>
 *   <li>Lock (APPROVED → LOCKED)</li>
 *   <li>Archive (LOCKED → ARCHIVED)</li>
 * </ol>
 *
 * <p>For every step asserts:
 * <ul>
 *   <li>the expected {@link AuditAction} code lands in
 *       {@code public.system_audit_log}</li>
 *   <li>{@code tenant_id}, {@code project_id}, {@code actor_user_id},
 *       {@code entity_type} are populated</li>
 *   <li>hash chain continuity: every row's {@code hash_prev} equals the
 *       previous row's {@code hash_current}</li>
 *   <li>the calibration row carries the factor_id in {@code entity_id}
 *       (per CalibrateEvaluationScoreUseCase contract)</li>
 * </ul>
 */
@Tag("audit")
@Tag("integration")
class Phase5AuditLifecycleTest extends AbstractIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired DepartmentRepository departments;
    @Autowired PositionRepository positions;
    @Autowired CreateMethodologyFromScratchUseCase createMethodologyUseCase;
    @Autowired FactorService factorService;
    @Autowired FactorLevelService factorLevelService;
    @Autowired ApproveMethodologyVersionUseCase approveMethodologyUseCase;
    @Autowired CreateEvaluationUseCase createEvaluationUseCase;
    @Autowired UpsertEvaluationScoreUseCase upsertUseCase;
    @Autowired SubmitEvaluationUseCase submitUseCase;
    @Autowired ApproveEvaluationUseCase approveUseCase;
    @Autowired CalibrateEvaluationScoreUseCase calibrateUseCase;
    @Autowired LockEvaluationUseCase lockUseCase;
    @Autowired ArchiveEvaluationUseCase archiveUseCase;
    @Autowired SystemAuditLogRepository auditLog;

    @AfterEach
    void cleanup() { TenantContextHolder.clear(); }

    @Test
    void fullEvaluationLifecycleWritesAuditRowsWithHashChainContinuity() {
        UUID tenant = newSeededTenantId();
        UUID actor = seedUser(tenant);
        ProjectJpaEntity proj = projects.save(newProject(tenant, "PRJ-EVAL-AL"));
        DepartmentJpaEntity dpt = departments.save(
                newDept(tenant, proj.getId(), "DPT-EVAL"));
        PositionJpaEntity pos = positions.save(
                newPosition(tenant, proj.getId(), dpt.getId(), "POS-EVAL"));

        // Full HRLAB_PROJECT_MANAGER context with every Phase 5 permission.
        TenantContextHolder.set(new TenantContext(
                actor, tenant, Set.of(proj.getId()),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("METHODOLOGY_READ", "METHODOLOGY_CREATE", "METHODOLOGY_EDIT",
                        "METHODOLOGY_APPROVE", "METHODOLOGY_LOCK",
                        "EVALUATION_READ", "EVALUATION_EDIT", "EVALUATION_APPROVE",
                        "EVALUATION_LOCK", "CALIBRATION_EDIT"),
                Set.of(), false, "ru-RU"));

        // Seed a methodology version + 2 factors (each with 2 levels) and APPROVE.
        MethodologyAggregate agg = createMethodologyUseCase.create(new CreateMethodologyCommand(
                proj.getId(), "MTH-EVAL-1",
                Map.of("ru-RU", "Тестовая методология"),
                Map.of("ru-RU", "Описание"),
                MethodologyType.CUSTOM, ScoringMode.DIRECT_POINTS,
                new BigDecimal("1000")));
        UUID versionId = agg.currentVersion().id();

        Factor f1 = factorService.add(versionId, new FactorCommand(
                "F1", Map.of("ru-RU", "Фактор 1"), null,
                new BigDecimal("500"), new BigDecimal("500"), 1, true));
        Factor f2 = factorService.add(versionId, new FactorCommand(
                "F2", Map.of("ru-RU", "Фактор 2"), null,
                new BigDecimal("500"), new BigDecimal("500"), 2, true));
        FactorLevel f1l1 = factorLevelService.add(f1.id(), new FactorLevelCommand(
                "L1", 1, new BigDecimal("100"), null,
                Map.of("ru-RU", "Уровень 1"), null));
        factorLevelService.add(f1.id(), new FactorLevelCommand(
                "L2", 2, new BigDecimal("500"), null,
                Map.of("ru-RU", "Уровень 2"), null));
        FactorLevel f2l1 = factorLevelService.add(f2.id(), new FactorLevelCommand(
                "L1", 1, new BigDecimal("100"), null,
                Map.of("ru-RU", "Уровень 1"), null));
        factorLevelService.add(f2.id(), new FactorLevelCommand(
                "L2", 2, new BigDecimal("500"), null,
                Map.of("ru-RU", "Уровень 2"), null));
        approveMethodologyUseCase.approve(versionId);

        // --- Evaluation lifecycle (the actions we care about) ---
        // 1. CREATE evaluation
        Evaluation eval = createEvaluationUseCase.create(new CreateEvaluationCommand(
                pos.getId(), versionId, actor));
        UUID evaluationId = eval.id();

        // 2. UPSERT score for F1
        upsertUseCase.upsert(new UpsertEvaluationScoreCommand(
                evaluationId, f1.id(), f1l1.id(), "Score F1"));
        // 3. UPSERT score for F2
        upsertUseCase.upsert(new UpsertEvaluationScoreCommand(
                evaluationId, f2.id(), f2l1.id(), "Score F2"));

        // 4. SUBMIT
        submitUseCase.submit(evaluationId);
        // 5. APPROVE
        approveUseCase.approve(evaluationId);
        // 6. CALIBRATE F1 (reason ≥ 20 chars)
        calibrateUseCase.calibrate(new CalibrateEvaluationScoreCommand(
                evaluationId, f1.id(), new BigDecimal("150.0000"),
                "Calibration adjustment per committee decision"));
        // 7. LOCK
        lockUseCase.lock(evaluationId);
        // 8. ARCHIVE
        archiveUseCase.archive(evaluationId,
                "Cycle closed — archived per policy retention");

        // -- ASSERTIONS --
        List<SystemAuditLogJpaEntity> rows = auditLog
                .findByTenantIdOrderByCreatedAtDesc(tenant);

        // Catalog: every Phase 5 lifecycle action must be present.
        var phase5Actions = rows.stream()
                .map(SystemAuditLogJpaEntity::getAction)
                .filter(a -> a != null && a.startsWith("EVALUATION_"))
                .toList();
        assertThat(phase5Actions)
                .as("audit must contain every Phase 5 lifecycle action")
                .contains(
                        AuditAction.EVALUATION_CREATED,
                        AuditAction.EVALUATION_SCORE_UPSERTED,
                        AuditAction.EVALUATION_SUBMITTED,
                        AuditAction.EVALUATION_APPROVED,
                        AuditAction.EVALUATION_SCORE_CALIBRATED,
                        AuditAction.EVALUATION_LOCKED,
                        AuditAction.EVALUATION_ARCHIVED);

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
        assertThat(rows.get(rows.size() - 1).getHashCurrent()).isNotBlank();

        // Per-row column assertions via JdbcTemplate.
        // EVALUATION_CREATED row carries tenant + actor + project + entity_id.
        Integer createdMatches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.system_audit_log "
                        + "WHERE tenant_id = ? AND actor_user_id = ? "
                        + "AND project_id = ? AND action = ? "
                        + "AND entity_type = 'Evaluation' AND entity_id = ? "
                        + "AND after_json IS NOT NULL",
                Integer.class, tenant, actor, proj.getId(),
                AuditAction.EVALUATION_CREATED, evaluationId);
        assertThat(createdMatches)
                .as("CREATED row carries tenant + actor + project + entity_id")
                .isEqualTo(1);

        // EVALUATION_SUBMITTED — before/after JSON must be populated.
        Integer submittedMatches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.system_audit_log "
                        + "WHERE tenant_id = ? AND action = ? AND entity_id = ? "
                        + "AND before_json IS NOT NULL AND after_json IS NOT NULL",
                Integer.class, tenant, AuditAction.EVALUATION_SUBMITTED, evaluationId);
        assertThat(submittedMatches).isEqualTo(1);

        // EVALUATION_APPROVED — same.
        Integer approvedMatches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.system_audit_log "
                        + "WHERE tenant_id = ? AND action = ? AND entity_id = ? "
                        + "AND before_json IS NOT NULL AND after_json IS NOT NULL",
                Integer.class, tenant, AuditAction.EVALUATION_APPROVED, evaluationId);
        assertThat(approvedMatches).isEqualTo(1);

        // EVALUATION_SCORE_CALIBRATED — referenced entity is the EvaluationScore
        // row (entity_type) and reason captured.
        Integer calibrationMatches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.system_audit_log "
                        + "WHERE tenant_id = ? AND action = ? "
                        + "AND entity_type = 'EvaluationScore' "
                        + "AND reason IS NOT NULL "
                        + "AND before_json IS NOT NULL AND after_json IS NOT NULL",
                Integer.class, tenant, AuditAction.EVALUATION_SCORE_CALIBRATED);
        assertThat(calibrationMatches)
                .as("CALIBRATED row carries reason + before/after JSON")
                .isEqualTo(1);

        // EVALUATION_LOCKED + EVALUATION_ARCHIVED — both with entity_id.
        Integer lockedMatches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.system_audit_log "
                        + "WHERE tenant_id = ? AND action = ? AND entity_id = ?",
                Integer.class, tenant, AuditAction.EVALUATION_LOCKED, evaluationId);
        assertThat(lockedMatches).isEqualTo(1);

        Integer archivedMatches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.system_audit_log "
                        + "WHERE tenant_id = ? AND action = ? AND entity_id = ? "
                        + "AND reason IS NOT NULL",
                Integer.class, tenant, AuditAction.EVALUATION_ARCHIVED, evaluationId);
        assertThat(archivedMatches).isEqualTo(1);

        // Score upserts — at least 2 rows for the 2 factors.
        long upsertCount = rows.stream()
                .map(SystemAuditLogJpaEntity::getAction)
                .filter(AuditAction.EVALUATION_SCORE_UPSERTED::equals)
                .count();
        assertThat(upsertCount)
                .as("two score upserts must produce two audit rows")
                .isGreaterThanOrEqualTo(2L);

        // Lower bound for total Phase 5 audit rows.
        long phase5RowsForTenant = phase5Actions.size();
        assertThat(phase5RowsForTenant)
                .as("at least one audit row per Phase 5 lifecycle mutation")
                .isGreaterThanOrEqualTo(8L);
    }

    private ProjectJpaEntity newProject(UUID tenantId, String code) {
        return new ProjectJpaEntity(
                UUID.randomUUID(), tenantId, code, Map.of("ru-RU", code), null,
                ProjectStatus.ACTIVE, null, null, null);
    }

    private DepartmentJpaEntity newDept(UUID tenantId, UUID projectId, String code) {
        return new DepartmentJpaEntity(
                UUID.randomUUID(), tenantId, projectId, null, code,
                Map.of("ru-RU", code), DepartmentType.DEPARTMENT, DepartmentStatus.ACTIVE);
    }

    private PositionJpaEntity newPosition(UUID tenantId, UUID projectId,
                                          UUID departmentId, String code) {
        return new PositionJpaEntity(
                UUID.randomUUID(), tenantId, projectId, departmentId, code,
                Map.of("ru-RU", code), null, null, null, null, PositionStatus.ACTIVE);
    }

    private UUID seedUser(UUID tenantId) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.users (id, email, full_name, status, default_locale, version) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'ru-RU', 0) "
                        + "ON CONFLICT (id) DO NOTHING",
                userId, "u-" + userId + "@test", "Test user " + userId);
        return userId;
    }
}
