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
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.integration.exports.application.CancelExportJobUseCase;
import uz.hrlab.grading.integration.exports.application.DownloadExportFileUseCase;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    @MockBean DownloadExportFileUseCase downloadFileUseCase;
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

    // --- F2 backstop: authenticated-but-wrong-permission → 403 ---------------
    // Each newly-@PreAuthorize-gated endpoint must deny an authenticated caller
    // holding only an irrelevant authority. Mirrors
    // streamingDownloadWithoutExportReadIs403.

    @Test
    void requestWithoutRequiredAuthorityIs403() throws Exception {
        // Valid body so @Valid passes and the @PreAuthorize gate is what denies.
        mvc.perform(post("/api/v1/exports/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"export_type\":\"POSITION_CATALOG\",\"format\":\"XLSX\",\"project_id\":\""
                                + UUID.randomUUID() + "\"}")
                        .with(jwt().authorities(() -> "SOME_OTHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listWithoutRequiredAuthorityIs403() throws Exception {
        mvc.perform(get("/api/v1/exports")
                        .with(jwt().authorities(() -> "SOME_OTHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getWithoutRequiredAuthorityIs403() throws Exception {
        mvc.perform(get("/api/v1/exports/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "SOME_OTHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void downloadUrlWithoutRequiredAuthorityIs403() throws Exception {
        mvc.perform(get("/api/v1/exports/{id}/download-url", UUID.randomUUID())
                        .with(jwt().authorities(() -> "SOME_OTHER")))
                .andExpect(status().isForbidden());
    }

    // --- Batch 2: backend-proxied streaming download endpoint ----------------

    @Test
    void streamingDownloadAnonymousIs401() throws Exception {
        mvc.perform(get("/api/v1/exports/{id}/download", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void streamingDownloadWithoutExportReadIs403() throws Exception {
        // Authenticated but missing EXPORT_READ -> @PreAuthorize denies (403).
        mvc.perform(get("/api/v1/exports/{id}/download", UUID.randomUUID())
                        .with(jwt().authorities(() -> "SOME_OTHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void streamingDownloadStreamsBytesWithAttachmentDisposition() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] body = new byte[]{1, 2, 3, 4};
        given(downloadFileUseCase.download(eq(id)))
                .willReturn(new DownloadExportFileUseCase.DownloadPayload(
                        body, "position_catalog_abcd1234.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        mvc.perform(get("/api/v1/exports/{id}/download", id)
                        .with(jwt().authorities(() -> "EXPORT_READ")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("position_catalog_abcd1234.xlsx")))
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().bytes(body));
    }

    @Test
    void streamingDownloadCrossTenantIs404() throws Exception {
        UUID id = UUID.randomUUID();
        given(downloadFileUseCase.download(eq(id)))
                .willThrow(new TenantAccessDeniedException());
        mvc.perform(get("/api/v1/exports/{id}/download", id)
                        .with(jwt().authorities(() -> "EXPORT_READ")))
                .andExpect(status().isNotFound());
    }

    @Test
    void streamingDownloadSalaryExportWithoutSalaryPermissionIs403() throws Exception {
        // Caller holds EXPORT_READ but the salary gate in the use case throws.
        UUID id = UUID.randomUUID();
        given(downloadFileUseCase.download(eq(id)))
                .willThrow(new PermissionDeniedException());
        mvc.perform(get("/api/v1/exports/{id}/download", id)
                        .with(jwt().authorities(() -> "EXPORT_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void streamingDownloadNonGeneratedIs400() throws Exception {
        UUID id = UUID.randomUUID();
        given(downloadFileUseCase.download(eq(id)))
                .willThrow(new ValidationException("EXPORT_NOT_READY: QUEUED"));
        mvc.perform(get("/api/v1/exports/{id}/download", id)
                        .with(jwt().authorities(() -> "EXPORT_READ")))
                .andExpect(status().isBadRequest());
    }
}
