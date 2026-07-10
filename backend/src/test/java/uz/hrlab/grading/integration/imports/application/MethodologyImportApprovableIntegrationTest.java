package uz.hrlab.grading.integration.imports.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.methodology.application.ApproveMethodologyVersionUseCase;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.infrastructure.FactorJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.project.domain.ProjectStatus;
import uz.hrlab.grading.project.infrastructure.ProjectJpaEntity;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack proof (real Postgres via Testcontainers) that a METHODOLOGY_FACTORS_V1
 * import yields a REAL, APPROVABLE methodology: parsed rows are fed through the
 * production {@link MethodologyFactorsRowCommitter} (writing via repositories,
 * exactly as {@code CommitImportBatchUseCase} drives it per row), then the created
 * DRAFT version is APPROVED through the production
 * {@link ApproveMethodologyVersionUseCase} — which requires ≥1 factor, ≥2
 * levels/factor, a primary-locale (ru-RU) factor name and a weight sum consistent
 * with the scoring mode.
 *
 * <p>Docker-gated via {@link AbstractIntegrationTest}: skipped (not failed) when
 * no local Docker daemon is present; runs against real Postgres in CI.
 *
 * <p>Seeding follows {@code CeoApprovalWorkflowIntegrationTest} (direct
 * project/methodology setup + {@code TenantContext} construction).
 */
@Tag("integration")
class MethodologyImportApprovableIntegrationTest extends AbstractIntegrationTest {

    @Autowired MethodologyFactorsRowCommitter committer;
    @Autowired ProjectRepository projects;
    @Autowired MethodologyRepository methodologies;
    @Autowired MethodologyVersionRepository versions;
    @Autowired FactorRepository factors;
    @Autowired FactorLevelRepository levels;
    @Autowired ApproveMethodologyVersionUseCase approveMethodology;

    private static final String METHODOLOGY_CODE = "IMP-MTH";

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    void importedMethodology_isApprovable() {
        UUID tenantId = newSeededTenantId();
        UUID actorUserId = seedUser(UUID.randomUUID());
        ProjectJpaEntity project = projects.save(new ProjectJpaEntity(
                UUID.randomUUID(), tenantId, "IMP-PR", Map.of("ru-RU", "Import project"),
                null, ProjectStatus.ACTIVE, null, null, null));

        ImportRowCommitContext ctx = new ImportRowCommitContext(
                tenantId, project.getId(), actorUserId, UUID.randomUUID(), "trace-imp");

        // 2 factors × 2 levels, weights 50 + 50 = 100 → valid WEIGHTED_POINTS sum.
        for (Map<String, String> row : sampleRows()) {
            committer.commit(row, ctx);
        }

        // The committer created a DRAFT methodology + v1 with the factors/levels.
        MethodologyJpaEntity methodology = methodologies
                .findByTenantIdAndProjectIdAndCode(tenantId, project.getId(), METHODOLOGY_CODE)
                .orElseThrow();
        assertThat(methodology.getNameI18n()).containsEntry("ru-RU", "Импортированная методология");

        MethodologyVersionJpaEntity version = versions
                .findFirstByTenantIdAndMethodologyIdOrderByVersionNumberDesc(tenantId, methodology.getId())
                .orElseThrow();
        assertThat(version.getStatus()).isEqualTo(MethodologyVersionStatus.DRAFT);

        List<FactorJpaEntity> factorRows = factors
                .findAllByTenantIdAndMethodologyVersionIdOrderBySortOrderAsc(tenantId, version.getId());
        assertThat(factorRows).hasSize(2);
        for (FactorJpaEntity f : factorRows) {
            assertThat(levels.findAllByTenantIdAndFactorIdOrderByLevelOrderAsc(tenantId, f.getId()))
                    .as("factor %s must have >= 2 levels", f.getCode())
                    .hasSizeGreaterThanOrEqualTo(2);
        }

        // APPROVE through the production use-case — the whole point of the import.
        TenantContextHolder.set(new TenantContext(
                actorUserId, tenantId, Set.of(project.getId()),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("METHODOLOGY_READ", "METHODOLOGY_CREATE", "METHODOLOGY_EDIT",
                        "METHODOLOGY_APPROVE", "METHODOLOGY_LOCK"),
                Set.of(), false, "ru-RU"));
        approveMethodology.approve(version.getId());
        TenantContextHolder.clear();

        MethodologyVersionJpaEntity approved = versions
                .findByIdAndTenantId(version.getId(), tenantId).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(MethodologyVersionStatus.APPROVED);
        assertThat(approved.getApprovedBy()).isEqualTo(actorUserId);
        assertThat(approved.getApprovedAt()).isNotNull();
    }

    // --------------------------------------------------------------------- helper

    private static List<Map<String, String>> sampleRows() {
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(row("KNOWLEDGE", "Knowledge", "50", "L1", "Basic", "40", "1"));
        rows.add(row("KNOWLEDGE", "Knowledge", "50", "L2", "Advanced", "80", "2"));
        rows.add(row("EXPERIENCE", "Experience", "50", "L1", "Basic", "40", "1"));
        rows.add(row("EXPERIENCE", "Experience", "50", "L2", "Advanced", "80", "2"));
        return rows;
    }

    private static Map<String, String> row(String factorCode, String factorName, String weight,
                                           String levelCode, String levelName, String score,
                                           String levelOrder) {
        Map<String, String> r = new LinkedHashMap<>();
        // Methodology-level metadata — identical on every row (one per file).
        r.put("methodology_code", METHODOLOGY_CODE);
        r.put("methodology_name", "Импортированная методология");
        r.put("methodology_type", "CUSTOM");
        r.put("scoring_mode", "WEIGHTED_POINTS");
        r.put("target_total_points", "1000");
        // Per-factor/level columns.
        r.put("factor_code", factorCode);
        r.put("factor_name", factorName);
        r.put("weight", weight);
        r.put("score", score);
        r.put("level_code", levelCode);
        r.put("level_name", levelName);
        r.put("level_order", levelOrder);
        return r;
    }
}
