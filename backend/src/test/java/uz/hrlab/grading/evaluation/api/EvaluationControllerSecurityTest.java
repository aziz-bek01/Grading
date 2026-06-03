package uz.hrlab.grading.evaluation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.api.GlobalExceptionHandler;
import uz.hrlab.grading.common.api.WebMvcSecurityTestConfig;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.evaluation.application.ApproveEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.ArchiveEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.BulkSubmitEvaluationsUseCase;
import uz.hrlab.grading.evaluation.application.BulkUpsertEvaluationScoreUseCase;
import uz.hrlab.grading.evaluation.application.CalibrateEvaluationScoreUseCase;
import uz.hrlab.grading.evaluation.application.CreateEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.EvaluationQueries;
import uz.hrlab.grading.evaluation.application.LockEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.PreviewEvaluationScoreUseCase;
import uz.hrlab.grading.evaluation.application.RequestEvaluationChangesUseCase;
import uz.hrlab.grading.evaluation.application.SubmitEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.UpsertEvaluationScoreUseCase;
import uz.hrlab.grading.evaluation.domain.Evaluation;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("security")
@WebMvcTest(controllers = {EvaluationController.class},
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class EvaluationControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean CreateEvaluationUseCase createUseCase;
    @MockBean UpsertEvaluationScoreUseCase upsertScoreUseCase;
    @MockBean SubmitEvaluationUseCase submitUseCase;
    @MockBean ApproveEvaluationUseCase approveUseCase;
    @MockBean RequestEvaluationChangesUseCase requestChangesUseCase;
    @MockBean LockEvaluationUseCase lockUseCase;
    @MockBean ArchiveEvaluationUseCase archiveUseCase;
    @MockBean CalibrateEvaluationScoreUseCase calibrateUseCase;
    @MockBean PreviewEvaluationScoreUseCase previewUseCase;
    @MockBean BulkUpsertEvaluationScoreUseCase bulkUpsertScoreUseCase;
    @MockBean BulkSubmitEvaluationsUseCase bulkSubmitUseCase;
    @MockBean EvaluationQueries queries;
    @MockBean AuditService auditService;

    @Test
    void anonymousListIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/evaluations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listWithoutEvaluationReadIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/evaluations").with(jwt().authorities(() -> "ORG_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listWithReadReturns200() throws Exception {
        given(queries.list(any(), any(), any(), any(), any()))
                .willReturn((Page) new PageImpl<>(List.of()));
        mvc.perform(get("/api/v1/evaluations").with(jwt().authorities(() -> "EVALUATION_READ")))
                .andExpect(status().isOk());
    }

    @Test
    void createRequiresEvaluationEdit() throws Exception {
        mvc.perform(post("/api/v1/evaluations")
                        .with(jwt().authorities(() -> "EVALUATION_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void createWithEditReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        given(createUseCase.create(any())).willReturn(sampleEvaluation(id));
        mvc.perform(post("/api/v1/evaluations")
                        .with(jwt().authorities(() -> "EVALUATION_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated());
    }

    @Test
    void approveRequiresEvaluationApprove() throws Exception {
        mvc.perform(post("/api/v1/evaluations/{id}/approve", UUID.randomUUID())
                        .with(jwt().authorities(() -> "EVALUATION_EDIT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void lockRequiresEvaluationLock() throws Exception {
        mvc.perform(post("/api/v1/evaluations/{id}/lock", UUID.randomUUID())
                        .with(jwt().authorities(() -> "EVALUATION_APPROVE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void calibrateRequiresCalibrationEdit() throws Exception {
        mvc.perform(post("/api/v1/evaluations/{id}/calibrate", UUID.randomUUID())
                        .with(jwt().authorities(() -> "EVALUATION_APPROVE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(calibrateBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void calibrateRejectsReasonShorterThan20Chars() throws Exception {
        mvc.perform(post("/api/v1/evaluations/{id}/calibrate", UUID.randomUUID())
                        .with(jwt().authorities(() -> "CALIBRATION_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "factor_id": "00000000-0000-0000-0000-000000000001",
                                  "new_raw_factor_score": 100,
                                  "reason": "too short"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownIdReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(queries.findById(id)).willThrow(new TenantAccessDeniedException());
        mvc.perform(get("/api/v1/evaluations/{id}", id)
                        .with(jwt().authorities(() -> "EVALUATION_READ")))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedUuidReturns400() throws Exception {
        mvc.perform(get("/api/v1/evaluations/{id}", "not-a-uuid")
                        .with(jwt().authorities(() -> "EVALUATION_READ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createIgnoresTenantIdInBody() throws Exception {
        UUID id = UUID.randomUUID();
        given(createUseCase.create(any())).willReturn(sampleEvaluation(id));
        String body = """
                {
                  "tenantId": "00000000-0000-0000-0000-000000000001",
                  "tenant_id": "00000000-0000-0000-0000-000000000002",
                  "position_id": "00000000-0000-0000-0000-000000000010",
                  "methodology_version_id": "00000000-0000-0000-0000-000000000020"
                }
                """;
        mvc.perform(post("/api/v1/evaluations")
                        .with(jwt().authorities(() -> "EVALUATION_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listByFactorReturnsFactorRowsWhenGroupBySet() throws Exception {
        // groupBy=factor branches to listByFactor and returns Page<EvaluationByFactorRow>.
        UUID factorId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        given(queries.listByFactor(any(), any(), any(), any(), any()))
                .willReturn((Page) new PageImpl<>(List.of()));
        mvc.perform(get("/api/v1/evaluations")
                        .param("groupBy", "factor")
                        .param("factorId", factorId.toString())
                        .param("projectId", projectId.toString())
                        .with(jwt().authorities(() -> "EVALUATION_READ")))
                .andExpect(status().isOk());
    }

    @Test
    void bulkScoreRequiresEvaluationEdit() throws Exception {
        UUID factorId = UUID.randomUUID();
        mvc.perform(post("/api/v1/evaluations/factor/{factorId}/bulk-score", factorId)
                        .with(jwt().authorities(() -> "EVALUATION_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "evaluation_ids": ["00000000-0000-0000-0000-000000000001"],
                                  "factor_level_id": "00000000-0000-0000-0000-000000000002",
                                  "reason": "test"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void bulkScoreRejectsEmptyIdList() throws Exception {
        UUID factorId = UUID.randomUUID();
        mvc.perform(post("/api/v1/evaluations/factor/{factorId}/bulk-score", factorId)
                        .with(jwt().authorities(() -> "EVALUATION_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "evaluation_ids": [],
                                  "factor_level_id": "00000000-0000-0000-0000-000000000002"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkSubmitRequiresEvaluationEdit() throws Exception {
        UUID factorId = UUID.randomUUID();
        mvc.perform(post("/api/v1/evaluations/factor/{factorId}/bulk-submit", factorId)
                        .with(jwt().authorities(() -> "EVALUATION_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "evaluation_ids": ["00000000-0000-0000-0000-000000000001"],
                                  "reason": "test"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void previewRequiresEvaluationRead() throws Exception {
        mvc.perform(post("/api/v1/evaluations/preview-score")
                        .with(jwt().authorities(() -> "ORG_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"methodology_version_id": "00000000-0000-0000-0000-000000000010"}
                                """))
                .andExpect(status().isForbidden());
    }

    private String createBody() {
        return """
                {
                  "position_id": "00000000-0000-0000-0000-000000000010",
                  "methodology_version_id": "00000000-0000-0000-0000-000000000020"
                }
                """;
    }

    private String calibrateBody() {
        return """
                {
                  "factor_id": "00000000-0000-0000-0000-000000000001",
                  "new_raw_factor_score": 100,
                  "reason": "Manual adjustment after committee deliberation review."
                }
                """;
    }

    private Evaluation sampleEvaluation(UUID id) {
        return new Evaluation(id, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                EvaluationStatus.DRAFT,
                BigDecimal.ZERO, BigDecimal.ZERO,
                null, null,
                null, null, null, null, null, null, null, null);
    }
}
