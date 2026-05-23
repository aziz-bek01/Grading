package uz.hrlab.grading.organization.api;

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
import uz.hrlab.grading.organization.application.ArchiveDepartmentUseCase;
import uz.hrlab.grading.organization.application.CreateDepartmentUseCase;
import uz.hrlab.grading.organization.application.FindDepartmentQuery;
import uz.hrlab.grading.organization.application.UpdateDepartmentUseCase;
import uz.hrlab.grading.organization.domain.Department;
import uz.hrlab.grading.organization.domain.DepartmentStatus;
import uz.hrlab.grading.organization.domain.DepartmentType;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-203 — HTTP-layer security smoke for {@link DepartmentController}. Mirrors
 * {@code ProjectControllerSecurityTest}: deny-by-default, RBAC, malformed UUID,
 * tenantId-in-body ignored, unknown id ⇒ 404, archive permission.
 */
@Tag("security")
@WebMvcTest(controllers = DepartmentController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class DepartmentControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean CreateDepartmentUseCase createUseCase;
    @MockBean UpdateDepartmentUseCase updateUseCase;
    @MockBean ArchiveDepartmentUseCase archiveUseCase;
    @MockBean FindDepartmentQuery findQuery;
    @MockBean AuditService auditService; // required by GlobalExceptionHandler

    // ---------- 1) Anonymous → 401 ----------
    @Test
    void anonymousGetTreeIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/departments/tree").param("projectId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 2) Wrong authority → 403 ----------
    @Test
    void getTreeWithoutOrgReadReturns403() throws Exception {
        mvc.perform(get("/api/v1/departments/tree")
                        .param("projectId", UUID.randomUUID().toString())
                        .with(jwt().authorities(() -> "POSITION_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void postWithoutOrgEditReturns403() throws Exception {
        mvc.perform(post("/api/v1/departments")
                        .with(jwt().authorities(() -> "ORG_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isForbidden());
    }

    // ---------- 3) Correct authority → 2xx ----------
    @Test
    void getTreeWithOrgReadReturns200() throws Exception {
        given(findQuery.tree(any(UUID.class))).willReturn(java.util.List.of());
        mvc.perform(get("/api/v1/departments/tree")
                        .param("projectId", UUID.randomUUID().toString())
                        .with(jwt().authorities(() -> "ORG_READ")))
                .andExpect(status().isOk());
    }

    @Test
    void createWithOrgEditReturns201() throws Exception {
        Department created = new Department(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, "HR",
                Map.of("ru-RU", "Отдел"), DepartmentType.DEPARTMENT, DepartmentStatus.ACTIVE);
        given(createUseCase.create(any())).willReturn(created);

        mvc.perform(post("/api/v1/departments")
                        .with(jwt().authorities(() -> "ORG_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated());
    }

    // ---------- 4) tenantId in body is ignored ----------
    @Test
    void bodyTenantIdFieldIsIgnoredByJacksonOnRecordDto() throws Exception {
        Department created = new Department(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, "HR",
                Map.of("ru-RU", "Отдел"), DepartmentType.DEPARTMENT, DepartmentStatus.ACTIVE);
        given(createUseCase.create(any())).willReturn(created);

        String bodyWithSpoofedTenant = """
                {
                  "tenantId": "00000000-0000-0000-0000-000000000001",
                  "tenant_id": "00000000-0000-0000-0000-000000000002",
                  "projectId": "%s",
                  "code": "HR",
                  "nameI18n": {"ru-RU": "Отдел"},
                  "type": "DEPARTMENT"
                }
                """.formatted(UUID.randomUUID());

        mvc.perform(post("/api/v1/departments")
                        .with(jwt().authorities(() -> "ORG_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithSpoofedTenant))
                .andExpect(status().isCreated());
    }

    // ---------- 5) Unknown ID → 404 (cross-tenant probing returns 404, not 403) ----------
    @Test
    void unknownIdReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(findQuery.findById(id)).willThrow(new TenantAccessDeniedException());
        mvc.perform(get("/api/v1/departments/{id}", id)
                        .with(jwt().authorities(() -> "ORG_READ")))
                .andExpect(status().isNotFound());
    }

    // ---------- 6) Malformed UUID → 400 ----------
    @Test
    void malformedUuidReturns400() throws Exception {
        mvc.perform(get("/api/v1/departments/{id}", "not-a-uuid")
                        .with(jwt().authorities(() -> "ORG_READ")))
                .andExpect(status().isBadRequest());
    }

    // ---------- 7) Archive without ORG_EDIT → 403 ----------
    @Test
    void archiveWithoutOrgEditReturns403() throws Exception {
        mvc.perform(post("/api/v1/departments/{id}/archive", UUID.randomUUID())
                        .with(jwt().authorities(() -> "ORG_READ")))
                .andExpect(status().isForbidden());
    }

    // ---------- 8) Patch without ORG_EDIT → 403 ----------
    @Test
    void patchWithoutOrgEditReturns403() throws Exception {
        mvc.perform(patch("/api/v1/departments/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "ORG_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nameI18n\":{\"ru-RU\":\"X\"}}"))
                .andExpect(status().isForbidden());
    }

    private String validCreateBody() throws Exception {
        return json.writeValueAsString(new CreateDepartmentRequest(
                UUID.randomUUID(), null, "HR",
                Map.of("ru-RU", "Отдел"), DepartmentType.DEPARTMENT));
    }
}
