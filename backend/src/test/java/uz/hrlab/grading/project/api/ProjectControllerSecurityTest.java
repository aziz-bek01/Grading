package uz.hrlab.grading.project.api;

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
import uz.hrlab.grading.project.application.ArchiveProjectUseCase;
import uz.hrlab.grading.project.application.CreateProjectUseCase;
import uz.hrlab.grading.project.application.FindProjectQuery;
import uz.hrlab.grading.project.application.LockProjectUseCase;
import uz.hrlab.grading.project.application.ProjectLockedException;
import uz.hrlab.grading.project.application.UpdateProjectUseCase;
import uz.hrlab.grading.project.domain.Project;
import uz.hrlab.grading.project.domain.ProjectStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-203 — HTTP-layer security smoke for {@link ProjectController}. Runs
 * without Docker; covers RBAC, anonymous denial, malformed UUID, locked
 * conflict, and the "tenantId field in body is ignored" guarantee.
 */
@Tag("security")
@WebMvcTest(controllers = ProjectController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class ProjectControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean CreateProjectUseCase createUseCase;
    @MockBean UpdateProjectUseCase updateUseCase;
    @MockBean ArchiveProjectUseCase archiveUseCase;
    @MockBean LockProjectUseCase lockUseCase;
    @MockBean FindProjectQuery findQuery;
    @MockBean AuditService auditService; // required by GlobalExceptionHandler

    // ---------- 1) Anonymous → 401 ----------
    @Test
    void anonymousGetIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());
    }

    // ---------- 2) Wrong authority → 403 ----------
    @Test
    void getWithoutProjectReadReturns403() throws Exception {
        mvc.perform(get("/api/v1/projects").with(jwt().authorities(() -> "ORG_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void postWithoutProjectCreateReturns403() throws Exception {
        mvc.perform(post("/api/v1/projects")
                        .with(jwt().authorities(() -> "PROJECT_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBodyJson()))
                .andExpect(status().isForbidden());
    }

    // ---------- 3) Correct authority → 2xx ----------
    @Test
    void getListWithProjectReadReturns200() throws Exception {
        given(findQuery.list(0, 20))
                .willReturn(new PageImpl<>(List.<Project>of()));
        mvc.perform(get("/api/v1/projects").with(jwt().authorities(() -> "PROJECT_READ")))
                .andExpect(status().isOk());
    }

    @Test
    void createWithProjectCreateReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        Project created = new Project(id, UUID.randomUUID(), "PRJ-1",
                Map.of("ru-RU", "Проект"), null, ProjectStatus.DRAFT, null, null, null);
        given(createUseCase.create(any())).willReturn(created);

        mvc.perform(post("/api/v1/projects")
                        .with(jwt().authorities(() -> "PROJECT_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBodyJson()))
                .andExpect(status().isCreated());
    }

    // ---------- 4) tenantId in body is ignored (TIP-12) ----------
    @Test
    void bodyTenantIdFieldIsIgnoredByJacksonOnRecordDto() throws Exception {
        // CreateProjectRequest has NO tenantId field; Jackson silently drops
        // unknown properties — backend uses TenantContext, never body. This
        // test asserts the request still completes 201 (i.e. the unknown field
        // is dropped) and confirms by ArchUnit (Rule 7) that no DTO field
        // matching tenantId exists.
        UUID id = UUID.randomUUID();
        Project created = new Project(id, UUID.randomUUID(), "PRJ-2",
                Map.of("ru-RU", "Проект"), null, ProjectStatus.DRAFT, null, null, null);
        given(createUseCase.create(any())).willReturn(created);

        String bodyWithSpoofedTenant = """
                {
                  "tenantId": "00000000-0000-0000-0000-000000000001",
                  "tenant_id": "00000000-0000-0000-0000-000000000002",
                  "code": "PRJ-2",
                  "name_i18n": {"ru-RU": "Проект"}
                }
                """;
        mvc.perform(post("/api/v1/projects")
                        .with(jwt().authorities(() -> "PROJECT_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithSpoofedTenant))
                .andExpect(status().isCreated());
    }

    // ---------- 5) LOCKED project → 409 on PATCH ----------
    @Test
    void patchOnLockedProjectReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new ProjectLockedException())
                .given(updateUseCase).update(eq(id), any());

        mvc.perform(patch("/api/v1/projects/{id}", id)
                        .with(jwt().authorities(() -> "PROJECT_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"x\"}"))
                .andExpect(status().isConflict());
    }

    // ---------- 6) Unknown ID → 404 ----------
    @Test
    void unknownIdReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(findQuery.findById(id)).willThrow(new TenantAccessDeniedException());
        mvc.perform(get("/api/v1/projects/{id}", id)
                        .with(jwt().authorities(() -> "PROJECT_READ")))
                .andExpect(status().isNotFound());
    }

    // ---------- 7) Malformed UUID → 400 ----------
    @Test
    void malformedUuidReturns400() throws Exception {
        mvc.perform(get("/api/v1/projects/{id}", "not-a-uuid")
                        .with(jwt().authorities(() -> "PROJECT_READ")))
                .andExpect(status().isBadRequest());
    }

    // ---------- 8) Lock with neither METHODOLOGY_LOCK nor PROJECT_EDIT → 403 ----------
    @Test
    void lockWithoutAnyEditAuthorityReturns403() throws Exception {
        mvc.perform(post("/api/v1/projects/{id}/lock", UUID.randomUUID())
                        .with(jwt().authorities(() -> "PROJECT_READ")))
                .andExpect(status().isForbidden());
    }

    private String validCreateBodyJson() throws Exception {
        return json.writeValueAsString(new CreateProjectRequest(
                "PRJ-OK", Map.of("ru-RU", "Имя"), null, LocalDate.now(), null));
    }
}
