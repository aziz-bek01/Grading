package uz.hrlab.grading.integration.exports.api;

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
import uz.hrlab.grading.integration.exports.application.CancelExportJobUseCase;
import uz.hrlab.grading.integration.exports.application.ExportJobQueries;
import uz.hrlab.grading.integration.exports.application.IssueDownloadUrlUseCase;
import uz.hrlab.grading.integration.exports.application.RequestExportUseCase;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("security")
@WebMvcTest(controllers = ExportController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class ExportControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean RequestExportUseCase requestUseCase;
    @MockBean ExportJobQueries queries;
    @MockBean IssueDownloadUrlUseCase issueUseCase;
    @MockBean CancelExportJobUseCase cancelUseCase;
    @MockBean AuditService audit;

    @Test
    void anonymousListIs401() throws Exception {
        mvc.perform(get("/api/v1/exports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousRequestIs401() throws Exception {
        mvc.perform(post("/api/v1/exports/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"export_type\":\"POSITION_CATALOG\",\"format\":\"XLSX\",\"project_id\":\""
                                + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousDownloadIs401() throws Exception {
        mvc.perform(get("/api/v1/exports/{id}/download-url", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownJobIs404() throws Exception {
        UUID id = UUID.randomUUID();
        given(queries.get(eq(id))).willThrow(new TenantAccessDeniedException());
        mvc.perform(get("/api/v1/exports/{id}", id)
                        .with(jwt().authorities(() -> "EXPORT_READ")))
                .andExpect(status().isNotFound());
    }

    @Test
    void listAuthenticatedReturns200() throws Exception {
        given(queries.list(any(), any(), any(), any()))
                .willReturn(org.springframework.data.domain.Page.empty());
        mvc.perform(get("/api/v1/exports")
                        .with(jwt().authorities(() -> "EXPORT_READ")))
                .andExpect(status().isOk());
    }

    @Test
    void cancelAnonymousIs401() throws Exception {
        mvc.perform(post("/api/v1/exports/{id}/cancel", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedUuidIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/exports/{id}", "not-a-uuid")
                        .with(jwt().authorities(() -> "EXPORT_READ")))
                .andExpect(status().isBadRequest());
    }

}
