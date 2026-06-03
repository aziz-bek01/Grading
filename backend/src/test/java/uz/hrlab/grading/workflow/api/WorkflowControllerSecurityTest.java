package uz.hrlab.grading.workflow.api;

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
import uz.hrlab.grading.workflow.application.AdvanceWorkflowStageUseCase;
import uz.hrlab.grading.workflow.application.GetProjectWorkflowQuery;
import uz.hrlab.grading.workflow.application.RecomputeWorkflowUseCase;
import uz.hrlab.grading.workflow.domain.ProjectWorkflow;
import uz.hrlab.grading.workflow.domain.WorkflowStage;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("security")
@WebMvcTest(controllers = WorkflowController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class WorkflowControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean GetProjectWorkflowQuery getQuery;
    @MockBean AdvanceWorkflowStageUseCase advanceUseCase;
    @MockBean RecomputeWorkflowUseCase recomputeUseCase;
    @MockBean AuditService audit;

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/projects/{id}/workflow-progress", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getWithoutPermissionIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/projects/{id}/workflow-progress", UUID.randomUUID())
                        .with(jwt().authorities(() -> "ORG_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getWithReadReturns200() throws Exception {
        UUID projectId = UUID.randomUUID();
        given(getQuery.get(eq(projectId))).willReturn(stubWorkflow(projectId));
        mvc.perform(get("/api/v1/projects/{id}/workflow-progress", projectId)
                        .with(jwt().authorities(() -> "WORKFLOW_READ")))
                .andExpect(status().isOk());
    }

    @Test
    void advanceRequiresWorkflowEditNotRead() throws Exception {
        mvc.perform(post("/api/v1/projects/{id}/workflow/advance", UUID.randomUUID())
                        .with(jwt().authorities(() -> "WORKFLOW_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_stage\":\"METHODOLOGY\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void advanceWithEditSucceeds() throws Exception {
        UUID projectId = UUID.randomUUID();
        given(advanceUseCase.advance(eq(projectId), any())).willReturn(stubWorkflow(projectId));
        mvc.perform(post("/api/v1/projects/{id}/workflow/advance", projectId)
                        .with(jwt().authorities(() -> "WORKFLOW_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_stage\":\"METHODOLOGY\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownProjectReturns404() throws Exception {
        UUID projectId = UUID.randomUUID();
        given(getQuery.get(eq(projectId))).willThrow(new TenantAccessDeniedException());
        mvc.perform(get("/api/v1/projects/{id}/workflow-progress", projectId)
                        .with(jwt().authorities(() -> "WORKFLOW_READ")))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedUuidReturns400() throws Exception {
        mvc.perform(get("/api/v1/projects/{id}/workflow-progress", "not-a-uuid")
                        .with(jwt().authorities(() -> "WORKFLOW_READ")))
                .andExpect(status().isBadRequest());
    }

    private ProjectWorkflow stubWorkflow(UUID projectId) {
        return new ProjectWorkflow(UUID.randomUUID(), UUID.randomUUID(), projectId,
                WorkflowStage.SETUP, null, null, List.of());
    }
}
