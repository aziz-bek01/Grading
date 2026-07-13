package uz.hrlab.grading.integration.imports.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.integration.excel.ExcelWriter;
import uz.hrlab.grading.integration.imports.domain.ImportBatchStatus;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchRepository;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack {@code ImportController} HTTP-flow proof for
 * {@code METHODOLOGY_FACTORS_V1}: {@code upload -> (async) validate -> commit
 * -> approve}, driven entirely through real REST endpoints (real Spring MVC
 * dispatch, real {@code TenantContextFilter} security chain, real
 * {@link uz.hrlab.grading.integration.imports.application.CommitImportBatchUseCase}
 * orchestration, real {@code MethodologyFactorsRowCommitter}, real Postgres via
 * Testcontainers) — NOT the committer called directly in isolation.
 *
 * <p>Coverage-gap rationale (QA gate finding): before this test, the ONLY
 * end-to-end proof that an imported methodology is approvable
 * ({@code MethodologyImportApprovableIntegrationTest}) calls
 * {@code MethodologyFactorsRowCommitter.commit(row, ctx)} directly in a
 * hand-rolled loop — it never exercises {@code CommitImportBatchUseCase}'s
 * real orchestration (two-pass loop, permission re-check, ABAC re-check,
 * terminal-status computation, per-row error recording), the real
 * {@code ExcelParser} parsing actual uploaded bytes, or the {@code /upload}
 * and {@code /commit} REST endpoints themselves. {@code ImportControllerSecurityTest}
 * covers the HTTP/authz boundary but MOCKS every use case, so it never proves
 * the wiring actually commits anything. This test closes that gap.
 *
 * <p>Authentication uses the {@code test}-profile {@code DevAuthFilter} header
 * contract (production traffic is real OIDC JWT; the header path is
 * allow-listed ONLY for {@code local}/{@code test} profiles — see
 * {@code DevAuthFilter.ALLOWED_PROFILES}), exactly like a real authenticated
 * request: it flows through the SAME {@code TenantContextFilter} that resolves
 * a real JWT, including the {@code user_tenant_memberships} fail-closed check
 * (F-205) — so the membership row is seeded exactly as production requires.
 *
 * <p>Docker-gated via {@link AbstractIntegrationTest}: skipped (not failed)
 * without a local Docker daemon; runs against real Postgres in CI. NOT
 * executable in this review environment (no Docker) — verified by
 * {@code mvn -DskipTests test-compile} only; a human/CI run is recommended
 * before relying on it as a merge gate.
 */
@AutoConfigureMockMvc
@Tag("integration")
class ImportControllerMethodologyFactorsEndToEndIntegrationTest extends AbstractIntegrationTest {

    private static final String METHODOLOGY_CODE = "IMP-HTTP-MTH";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ExcelWriter excelWriter;
    @Autowired ProjectRepository projects;
    @Autowired UserTenantMembershipRepository memberships;
    @Autowired ImportBatchRepository importBatches;
    @Autowired MethodologyRepository methodologies;
    @Autowired MethodologyVersionRepository versions;
    @Autowired FactorRepository factors;
    @Autowired FactorLevelRepository levels;

    @Test
    void uploadValidateCommitApprove_throughRealHttpEndpoints_persistsApprovableMethodology() throws Exception {
        UUID tenantId = newSeededTenantId();
        UUID userId = seedUser(UUID.randomUUID());
        // F-205: TenantContextFilter fail-closed requires a real membership row
        // for ANY authenticated tenant-scoped request, dev-auth included.
        memberships.save(new UserTenantMembershipJpaEntity(
                UUID.randomUUID(), userId, tenantId, MembershipStatus.ACTIVE, false));

        ProjectJpaEntity project = projects.save(new ProjectJpaEntity(
                UUID.randomUUID(), tenantId, "IMP-HTTP-PR", Map.of("ru-RU", "Import HTTP project"),
                null, ProjectStatus.ACTIVE, null, null, null));
        UUID projectId = project.getId();

        byte[] xlsx = weightedScaleMethodologyXlsx();
        MockMultipartFile file = new MockMultipartFile(
                "file", "methodology.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        // ---------------------------------------------------------------- upload
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/imports/upload")
                        .file(file)
                        .param("templateCode", "METHODOLOGY_FACTORS_V1")
                        .param("projectId", projectId.toString())
                        .with(devAuth(userId, tenantId, projectId, "METHODOLOGY_IMPORT")))
                .andExpect(status().isOk())
                .andReturn();
        ImportBatchResponse uploaded = objectMapper.readValue(
                uploadResult.getResponse().getContentAsString(), ImportBatchResponse.class);
        UUID batchId = uploaded.id();
        assertThat(batchId).isNotNull();

        // ------------------------------------------------- wait for async validation
        // UploadImportFileUseCase dispatches ImportProcessingJob on
        // importWorkerExecutor AFTER the upload transaction commits — poll the
        // real DB row (bounded) until the pipeline lands on a pre-commit terminal
        // state, exactly as a UI polling GET /api/v1/imports/{id} would observe.
        ImportBatchStatus finalPreCommitStatus = awaitStatus(batchId, tenantId,
                ImportBatchStatus.READY_FOR_REVIEW, ImportBatchStatus.VALIDATION_FAILED,
                ImportBatchStatus.DEAD_LETTER);
        assertThat(finalPreCommitStatus)
                .as("a well-formed METHODOLOGY_FACTORS_V1 file must validate cleanly")
                .isEqualTo(ImportBatchStatus.READY_FOR_REVIEW);

        // ---------------------------------------------------------------- commit
        MvcResult commitResult = mockMvc.perform(post("/api/v1/imports/{id}/commit", batchId)
                        .with(devAuth(userId, tenantId, projectId, "METHODOLOGY_IMPORT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMMITTED"))
                // API serializes snake_case (committed_row_count), not camelCase.
                .andExpect(jsonPath("$.committed_row_count").value(4))
                .andReturn();
        ImportBatchResponse committed = objectMapper.readValue(
                commitResult.getResponse().getContentAsString(), ImportBatchResponse.class);
        assertThat(committed.status()).isEqualTo(ImportBatchStatus.COMMITTED);

        // -------------------------------------------------------- verify persistence
        MethodologyJpaEntity methodology = methodologies
                .findByTenantIdAndProjectIdAndCode(tenantId, projectId, METHODOLOGY_CODE)
                .orElseThrow(() -> new AssertionError("methodology not persisted by real commit endpoint"));
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

        // ---------------------------------------------------------------- approve
        mockMvc.perform(post("/api/v1/methodology-versions/{id}/approve", version.getId())
                        .with(devAuth(userId, tenantId, projectId, "METHODOLOGY_APPROVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        MethodologyVersionJpaEntity approved = versions
                .findByIdAndTenantId(version.getId(), tenantId).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(MethodologyVersionStatus.APPROVED);
        assertThat(approved.getApprovedBy()).isEqualTo(userId);
    }

    // --------------------------------------------------------------------- helpers

    /**
     * DevAuthFilter header contract (test/local profiles only). {@code
     * HRLAB_PROJECT_MANAGER} bypasses {@code ProjectMembershipPolicy}'s
     * per-project assignment check; {@code X-Dev-Projects} is still supplied
     * for defence in depth, mirroring {@code MethodologyImportApprovableIntegrationTest}.
     */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor devAuth(
            UUID userId, UUID tenantId, UUID projectId, String permission) {
        return request -> {
            request.addHeader("X-Dev-User", userId.toString());
            request.addHeader("X-Dev-Tenant", tenantId.toString());
            request.addHeader("X-Dev-Projects", projectId.toString());
            request.addHeader("X-Dev-Roles", "HRLAB_PROJECT_MANAGER");
            request.addHeader("X-Dev-Permissions", permission);
            request.addHeader("X-Dev-Locale", "ru-RU");
            return request;
        };
    }

    private ImportBatchStatus awaitStatus(UUID batchId, UUID tenantId,
                                          ImportBatchStatus... terminalCandidates) throws InterruptedException {
        java.util.Set<ImportBatchStatus> terminal = java.util.Set.of(terminalCandidates);
        for (int attempt = 0; attempt < 100; attempt++) {
            Optional<uz.hrlab.grading.integration.imports.infrastructure.ImportBatchJpaEntity> found =
                    importBatches.findByIdAndTenantId(batchId, tenantId);
            if (found.isPresent() && terminal.contains(found.get().getStatus())) {
                return found.get().getStatus();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Import batch " + batchId
                + " did not reach a terminal pre-commit status within 10s");
    }

    /**
     * WEIGHTED_SCALE methodology: 2 factors x 2 levels, weight 50 + 50 = 100 =
     * target_total_points (approve-time invariant), each level carrying a
     * scale_value (the P0 fix's column). Header order matches
     * {@code ImportTemplateRegistry.METHODOLOGY_FACTORS_V1.requiredColumns()}
     * (weight/score are the primary columns the committer reads).
     */
    private byte[] weightedScaleMethodologyXlsx() {
        List<String> headers = List.of(
                "methodology_code", "methodology_name", "methodology_type", "scoring_mode",
                "target_total_points", "factor_code", "factor_name", "level_code", "level_name",
                "weight", "score", "scale_value");
        List<Map<String, String>> rows = List.of(
                row("KNOWLEDGE", "Knowledge", "50", "L1", "Basic", "10", "1"),
                row("KNOWLEDGE", "Knowledge", "50", "L2", "Advanced", "20", "2"),
                row("EXPERIENCE", "Experience", "50", "L1", "Basic", "10", "1"),
                row("EXPERIENCE", "Experience", "50", "L2", "Advanced", "20", "2"));
        return excelWriter.write("Data", headers, rows);
    }

    private static Map<String, String> row(String factorCode, String factorName, String weight,
                                           String levelCode, String levelName, String score,
                                           String scaleValue) {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("methodology_code", METHODOLOGY_CODE);
        r.put("methodology_name", "HTTP Imported Methodology");
        r.put("methodology_type", "CUSTOM");
        r.put("scoring_mode", "WEIGHTED_SCALE");
        r.put("target_total_points", "100");
        r.put("factor_code", factorCode);
        r.put("factor_name", factorName);
        r.put("level_code", levelCode);
        r.put("level_name", levelName);
        r.put("weight", weight);
        r.put("score", score);
        r.put("scale_value", scaleValue);
        return r;
    }
}
