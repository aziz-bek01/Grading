package uz.hrlab.grading.access.api;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import uz.hrlab.grading.access.application.ListRolesQuery;
import uz.hrlab.grading.access.application.RolePermissionAdminUseCase;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.api.GlobalExceptionHandler;
import uz.hrlab.grading.common.api.WebMvcSecurityTestConfig;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer smoke for {@link RoleController} (slice E1 — role catalog).
 *
 * <p>Asserts:
 * <ol>
 *   <li>Anonymous → 401 (deny-by-default).</li>
 *   <li>Authenticated caller WITHOUT any of the catalog authorities → 403.</li>
 *   <li>Caller WITH {@code USER_LIST} → 200; response carries the exact JSON
 *       contract (snake_case keys: {@code code}, {@code name_i18n},
 *       {@code scope}, {@code is_system}, {@code is_custom},
 *       {@code assignable_by_caller}, {@code reason_if_not}).</li>
 *   <li>{@code ?assignableOnly=true} is forwarded to the query.</li>
 * </ol>
 */
@Tag("security")
@WebMvcTest(controllers = RoleController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class RoleControllerSecurityTest {

    @Autowired MockMvc mvc;

    @MockBean ListRolesQuery listRolesQuery;
    @MockBean RolePermissionAdminUseCase rolePermissionAdmin;
    @MockBean AuditService auditService;

    // ------------------------------------------------------------------ 1)
    @Test
    void anonymousReturns401() throws Exception {
        mvc.perform(get("/api/v1/roles"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ 2)
    @Test
    void authenticatedWithoutCatalogAuthorityReturns403() throws Exception {
        mvc.perform(get("/api/v1/roles")
                        .with(jwt().authorities(new SimpleGrantedAuthority("PROJECT_READ"))))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 3)
    @Test
    void callerWithUserListReadsCatalogInExactContract() throws Exception {
        given(listRolesQuery.list(anyBoolean())).willReturn(List.of(
                new RoleResponse(
                        "HRLAB_SUPER_ADMIN",
                        Map.of("ru-RU", "HRLab Super Admin", "en-US", "HRLab Super Admin",
                                "uz-Cyrl-UZ", "HRLab Super Admin", "uz-Latn-UZ", "HRLab Super Admin"),
                        "PLATFORM", true, false, false, "HRLAB_ONLY"),
                new RoleResponse(
                        "CLIENT_HR_DIRECTOR",
                        Map.of("ru-RU", "Client HR Director", "en-US", "Client HR Director",
                                "uz-Cyrl-UZ", "Client HR Director", "uz-Latn-UZ", "Client HR Director"),
                        "TENANT", true, false, true, null)));

        mvc.perform(get("/api/v1/roles")
                        .with(jwt().authorities(new SimpleGrantedAuthority("USER_LIST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("HRLAB_SUPER_ADMIN"))
                .andExpect(jsonPath("$[0].name_i18n.['ru-RU']").value("HRLab Super Admin"))
                .andExpect(jsonPath("$[0].name_i18n.['uz-Cyrl-UZ']").value("HRLab Super Admin"))
                .andExpect(jsonPath("$[0].name_i18n.['uz-Latn-UZ']").value("HRLab Super Admin"))
                .andExpect(jsonPath("$[0].name_i18n.['en-US']").value("HRLab Super Admin"))
                .andExpect(jsonPath("$[0].scope").value("PLATFORM"))
                .andExpect(jsonPath("$[0].is_system").value(true))
                .andExpect(jsonPath("$[0].is_custom").value(false))
                .andExpect(jsonPath("$[0].assignable_by_caller").value(false))
                .andExpect(jsonPath("$[0].reason_if_not").value("HRLAB_ONLY"))
                .andExpect(jsonPath("$[1].code").value("CLIENT_HR_DIRECTOR"))
                .andExpect(jsonPath("$[1].scope").value("TENANT"))
                .andExpect(jsonPath("$[1].assignable_by_caller").value(true))
                // Contract: reason_if_not is `str|null`. The platform Jackson
                // config is `default-property-inclusion: non_null` (same policy
                // as active_tenant_id in CurrentUserResponse), so a null reason
                // is omitted from the JSON — the FE reads it as
                // undefined/absent, semantically equal to null.
                .andExpect(jsonPath("$[1].reason_if_not").doesNotExist());
    }

    // ------------------------------------------------------------------ 4)
    @Test
    void callerWithUserAccessManageMayReadCatalog() throws Exception {
        given(listRolesQuery.list(anyBoolean())).willReturn(List.of());

        mvc.perform(get("/api/v1/roles")
                        .param("assignableOnly", "true")
                        .with(jwt().authorities(new SimpleGrantedAuthority("USER_ACCESS_MANAGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ============================================================ E2 endpoints

    private static final UUID ROLE_ID = UUID.randomUUID();

    // GET /{roleId}/permissions — gate is USER_ACCESS_MANAGE only.
    @Test
    void getPermissionsRequiresUserAccessManage_403WithoutIt() throws Exception {
        mvc.perform(get("/api/v1/roles/" + ROLE_ID + "/permissions")
                        // USER_LIST is enough for the catalog GET but NOT for the matrix.
                        .with(jwt().authorities(new SimpleGrantedAuthority("USER_LIST"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPermissionsWithUserAccessManageReturns200() throws Exception {
        given(rolePermissionAdmin.getRolePermissions(any())).willReturn(
                new RolePermissionsResponse("CLIENT_HR_DIRECTOR", "TENANT", true, true,
                        List.of(new RolePermissionItem("PROJECT_READ", "PROJECT", "READ", true, false),
                                new RolePermissionItem("SALARY_VIEW", "SALARY", "VIEW", false, true))));

        mvc.perform(get("/api/v1/roles/" + ROLE_ID + "/permissions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("USER_ACCESS_MANAGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role_code").value("CLIENT_HR_DIRECTOR"))
                .andExpect(jsonPath("$.scope").value("TENANT"))
                .andExpect(jsonPath("$.is_system").value(true))
                .andExpect(jsonPath("$.editable_by_caller").value(true))
                .andExpect(jsonPath("$.items[0].code").value("PROJECT_READ"))
                .andExpect(jsonPath("$.items[0].granted").value(true))
                .andExpect(jsonPath("$.items[0].restricted").value(false))
                .andExpect(jsonPath("$.items[1].code").value("SALARY_VIEW"))
                .andExpect(jsonPath("$.items[1].restricted").value(true));
    }

    // PUT /{roleId}/permissions — gate is USER_ACCESS_MANAGE only.
    @Test
    void putPermissionsRequiresUserAccessManage_403WithoutIt() throws Exception {
        mvc.perform(put("/api/v1/roles/" + ROLE_ID + "/permissions")
                        .with(csrf())
                        .with(jwt().authorities(new SimpleGrantedAuthority("USER_LIST")))
                        .contentType(APPLICATION_JSON)
                        .content("{\"permission_codes\":[\"PROJECT_READ\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void putPermissionsWithUserAccessManageAcceptsSnakeCaseBody() throws Exception {
        given(rolePermissionAdmin.replaceRolePermissions(any(), any())).willReturn(
                new RolePermissionsResponse("CLIENT_HR_DIRECTOR", "TENANT", true, true,
                        List.of(new RolePermissionItem("PROJECT_READ", "PROJECT", "READ", true, false))));

        mvc.perform(put("/api/v1/roles/" + ROLE_ID + "/permissions")
                        .with(csrf())
                        .with(jwt().authorities(new SimpleGrantedAuthority("USER_ACCESS_MANAGE")))
                        .contentType(APPLICATION_JSON)
                        .content("{\"permission_codes\":[\"PROJECT_READ\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role_code").value("CLIENT_HR_DIRECTOR"))
                .andExpect(jsonPath("$.items[0].code").value("PROJECT_READ"));
    }
}
