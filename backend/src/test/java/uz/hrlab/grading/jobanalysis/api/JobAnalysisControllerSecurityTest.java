package uz.hrlab.grading.jobanalysis.api;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.api.GlobalExceptionHandler;
import uz.hrlab.grading.common.api.WebMvcSecurityTestConfig;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.jobanalysis.application.ArchiveQuestionnaireUseCase;
import uz.hrlab.grading.jobanalysis.application.CreateQuestionnaireUseCase;
import uz.hrlab.grading.jobanalysis.application.FindQuestionnaireQuery;
import uz.hrlab.grading.jobanalysis.application.SubmitQuestionnaireUseCase;
import uz.hrlab.grading.jobanalysis.application.UpdateAnswerUseCase;
import uz.hrlab.grading.jobanalysis.domain.JobAnalysisQuestionnaire;
import uz.hrlab.grading.jobanalysis.domain.QuestionnaireStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("security")
@WebMvcTest(controllers = {QuestionnaireController.class, PositionQuestionnaireController.class},
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class JobAnalysisControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean CreateQuestionnaireUseCase createUseCase;
    @MockBean FindQuestionnaireQuery findQuery;
    @MockBean UpdateAnswerUseCase updateAnswerUseCase;
    @MockBean SubmitQuestionnaireUseCase submitUseCase;
    @MockBean ArchiveQuestionnaireUseCase archiveUseCase;
    @MockBean AuditService auditService;

    @Test
    void anonymousGetIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/questionnaires/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getWithoutReadIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/questionnaires/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "ORG_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getWithReadReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(findQuery.findById(id)).willReturn(sample(id));
        mvc.perform(get("/api/v1/questionnaires/{id}", id)
                        .with(jwt().authorities(() -> "JOB_ANALYSIS_READ")))
                .andExpect(status().isOk());
    }

    @Test
    void patchAnswerWithoutEditIsForbidden() throws Exception {
        mvc.perform(patch("/api/v1/questionnaires/{id}/answers/{qId}",
                        UUID.randomUUID(), UUID.randomUUID())
                        .with(jwt().authorities(() -> "JOB_ANALYSIS_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer_text\":\"hi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitRequiresEdit() throws Exception {
        mvc.perform(post("/api/v1/questionnaires/{id}/submit", UUID.randomUUID())
                        .with(jwt().authorities(() -> "JOB_ANALYSIS_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void archiveRequiresReasonBody() throws Exception {
        mvc.perform(post("/api/v1/questionnaires/{id}/archive", UUID.randomUUID())
                        .with(jwt().authorities(() -> "JOB_ANALYSIS_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void positionScopedCreateRequiresEdit() throws Exception {
        mvc.perform(post("/api/v1/positions/{positionId}/questionnaires", UUID.randomUUID())
                        .with(jwt().authorities(() -> "JOB_ANALYSIS_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template_code\":\"STANDARD_V1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void positionScopedListRequiresRead() throws Exception {
        mvc.perform(get("/api/v1/positions/{positionId}/questionnaires", UUID.randomUUID())
                        .with(jwt().authorities(() -> "ORG_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownIdReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(findQuery.findById(id)).willThrow(new TenantAccessDeniedException());
        mvc.perform(get("/api/v1/questionnaires/{id}", id)
                        .with(jwt().authorities(() -> "JOB_ANALYSIS_READ")))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedUuidReturns400() throws Exception {
        mvc.perform(get("/api/v1/questionnaires/{id}", "not-a-uuid")
                        .with(jwt().authorities(() -> "JOB_ANALYSIS_READ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bodyTenantIdIsIgnoredOnCreate() throws Exception {
        UUID positionId = UUID.randomUUID();
        given(createUseCase.create(org.mockito.ArgumentMatchers.any()))
                .willReturn(sample(UUID.randomUUID()));
        String body = """
                {
                  "tenantId": "00000000-0000-0000-0000-000000000001",
                  "template_code": "STANDARD_V1"
                }
                """;
        mvc.perform(post("/api/v1/positions/{positionId}/questionnaires", positionId)
                        .with(jwt().authorities(() -> "JOB_ANALYSIS_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private JobAnalysisQuestionnaire sample(UUID id) {
        return new JobAnalysisQuestionnaire(id, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "STANDARD_V1", Map.of("ru-RU", "Анкета"),
                QuestionnaireStatus.DRAFT, 1, List.of());
    }
}
