package uz.hrlab.grading.integration.imports.api;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.api.GlobalExceptionHandler;
import uz.hrlab.grading.common.api.WebMvcSecurityTestConfig;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.integration.imports.application.CancelImportBatchUseCase;
import uz.hrlab.grading.integration.imports.application.CommitImportBatchUseCase;
import uz.hrlab.grading.integration.imports.application.ImportBatchQueries;
import uz.hrlab.grading.integration.imports.application.UploadImportFileUseCase;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("security")
@WebMvcTest(controllers = ImportController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class, FileUploadValidator.class})
class ImportControllerSecurityTest {

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean UploadImportFileUseCase upload;
    @MockBean ImportBatchQueries queries;
    @MockBean CommitImportBatchUseCase commit;
    @MockBean CancelImportBatchUseCase cancel;
    @MockBean AuditService audit;
    @MockBean uz.hrlab.grading.integration.imports.application.ImportTemplateSamples templateSamples;
    @MockBean uz.hrlab.grading.integration.imports.application.ImportTemplateRegistry templateRegistry;

    @Test
    void listAnonymousIs401() throws Exception {
        mvc.perform(get("/api/v1/imports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUnknownIs404ForAuthorisedUser() throws Exception {
        UUID id = UUID.randomUUID();
        given(queries.get(eq(id))).willThrow(new TenantAccessDeniedException());
        mvc.perform(get("/api/v1/imports/{id}", id)
                        .with(jwt().authorities(() -> "IMPORT_READ")))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadAnonymousIs401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "x.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1});
        mvc.perform(multipart("/api/v1/imports/upload")
                        .file(file)
                        .param("templateCode", "ORG_STRUCTURE_V1")
                        .param("projectId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedUuidIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/imports/{id}", "not-a-uuid")
                        .with(jwt().authorities(() -> "IMPORT_READ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void commitAnonymousIs401() throws Exception {
        mvc.perform(post("/api/v1/imports/{id}/commit", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancelAnonymousIs401() throws Exception {
        mvc.perform(post("/api/v1/imports/{id}/cancel", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadAuthenticatedHits200() throws Exception {
        UUID id = UUID.randomUUID();
        given(upload.upload(any(), any(), any())).willReturn(id);
        given(queries.get(eq(id))).willReturn(stubBatch(id));
        MockMultipartFile file = new MockMultipartFile("file", "x.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "header\nrow1".getBytes());
        mvc.perform(multipart("/api/v1/imports/upload")
                        .file(file)
                        .param("templateCode", "ORG_STRUCTURE_V1")
                        .param("projectId", UUID.randomUUID().toString())
                        .with(jwt().authorities(() -> "ORG_IMPORT")))
                .andExpect(status().isOk());
    }

    @Test
    void listAuthenticatedReturns200() throws Exception {
        given(queries.list(any(), any(), any()))
                .willReturn(org.springframework.data.domain.Page.empty());
        mvc.perform(get("/api/v1/imports")
                        .with(jwt().authorities(() -> "IMPORT_READ")))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    //  F3 — Level-1 file validation at the controller boundary.
    //  Each rejection must return 400 with the precise error code AND
    //  must NOT reach the upload use case.
    // ------------------------------------------------------------------

    @Test
    void rejectsEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "x.xlsx", XLSX_MIME, new byte[0]);
        mvc.perform(multipart("/api/v1/imports/upload")
                        .file(file)
                        .param("templateCode", "ORG_STRUCTURE_V1")
                        .param("projectId", UUID.randomUUID().toString())
                        .with(jwt().authorities(() -> "ORG_IMPORT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"));
        verify(upload, never()).upload(any(), any(), any());
    }

    @Test
    void rejectsOversizedFile() throws Exception {
        // 25MB + 1 byte
        int oversize = (int) (25L * 1024 * 1024 + 1);
        byte[] huge = new byte[oversize];
        MockMultipartFile file = new MockMultipartFile("file", "x.xlsx", XLSX_MIME, huge);
        mvc.perform(multipart("/api/v1/imports/upload")
                        .file(file)
                        .param("templateCode", "ORG_STRUCTURE_V1")
                        .param("projectId", UUID.randomUUID().toString())
                        .with(jwt().authorities(() -> "ORG_IMPORT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        verify(upload, never()).upload(any(), any(), any());
    }

    @Test
    void rejectsWrongMimeType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "x.xlsx",
                "text/csv", "header\nrow".getBytes());
        mvc.perform(multipart("/api/v1/imports/upload")
                        .file(file)
                        .param("templateCode", "ORG_STRUCTURE_V1")
                        .param("projectId", UUID.randomUUID().toString())
                        .with(jwt().authorities(() -> "ORG_IMPORT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MIME_TYPE"));
        verify(upload, never()).upload(any(), any(), any());
    }

    @Test
    void rejectsNonXlsxExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "evil.exe",
                XLSX_MIME, new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/v1/imports/upload")
                        .file(file)
                        .param("templateCode", "ORG_STRUCTURE_V1")
                        .param("projectId", UUID.randomUUID().toString())
                        .with(jwt().authorities(() -> "ORG_IMPORT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXTENSION"));
        verify(upload, never()).upload(any(), any(), any());
    }

    @Test
    void rejectsPathTraversalFilename() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "../etc/passwd.xlsx",
                XLSX_MIME, new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/v1/imports/upload")
                        .file(file)
                        .param("templateCode", "ORG_STRUCTURE_V1")
                        .param("projectId", UUID.randomUUID().toString())
                        .with(jwt().authorities(() -> "ORG_IMPORT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILENAME"));
        verify(upload, never()).upload(any(), any(), any());
    }

    private uz.hrlab.grading.integration.imports.infrastructure.ImportBatchJpaEntity stubBatch(UUID id) {
        return new uz.hrlab.grading.integration.imports.infrastructure.ImportBatchJpaEntity(
                id, UUID.randomUUID(), UUID.randomUUID(), "ORG_STRUCTURE_V1",
                uz.hrlab.grading.integration.imports.domain.ImportBatchStatus.UPLOADED,
                "x.xlsx", "tenants/x/projects/y/imports/z/original.xlsx",
                10L, "abc", UUID.randomUUID(), java.time.OffsetDateTime.now());
    }
}
