package uz.hrlab.grading.access.api;

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
import org.springframework.test.web.servlet.MockMvc;
import uz.hrlab.grading.access.application.GetCurrentUserUseCase;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.api.GlobalExceptionHandler;
import uz.hrlab.grading.common.api.WebMvcSecurityTestConfig;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-TI-005 — HTTP-layer smoke for {@link CurrentUserController}.
 *
 * <p>Asserts:
 * <ol>
 *   <li>Anonymous → 401 (deny-by-default).</li>
 *   <li>Authenticated caller with NO extra permission → 200 (every user can
 *       read their own identity).</li>
 *   <li>Response carries the complete {@code tenants[]} list — frontend
 *       contract requires {id, slug, brand_name, fingerprint_hue}.</li>
 *   <li>{@code activeTenantId} is surfaced when present.</li>
 *   <li>{@code permissions} reflect the JWT scope (NOT inherited from any
 *       other tenant).</li>
 * </ol>
 */
@Tag("security")
@WebMvcTest(controllers = CurrentUserController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class CurrentUserControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean GetCurrentUserUseCase getCurrentUserUseCase;
    @MockBean AuditService auditService;

    private static final UUID USER_ID = UUID.fromString("aaaa1111-aaaa-1111-aaaa-1111aaaa1111");
    private static final UUID ACME_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BETA_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    // ------------------------------------------------------------------ 1)
    @Test
    void anonymousReturns401() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ 2)
    @Test
    void authenticatedUserWithoutExtraPermissionReturns200() throws Exception {
        givenCurrentUserStub(List.of(acmeCard(), betaCard()), ACME_ID,
                Set.of("HRLAB_SUPER_ADMIN"), Set.of("PROJECT_READ"));

        mvc.perform(get("/api/v1/users/me").with(jwt()))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 3)
    @Test
    void responseListsAllTenantMembershipsNotJustActive() throws Exception {
        givenCurrentUserStub(List.of(acmeCard(), betaCard()), ACME_ID,
                Set.of("HRLAB_SUPER_ADMIN"), Set.of("PROJECT_READ"));

        mvc.perform(get("/api/v1/users/me").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenants.length()").value(2))
                .andExpect(jsonPath("$.tenants[0].id").value(ACME_ID.toString()))
                .andExpect(jsonPath("$.tenants[0].slug").value("acme"))
                .andExpect(jsonPath("$.tenants[0].brand_name").value("ACME"))
                .andExpect(jsonPath("$.tenants[0].fingerprint_hue").isNumber())
                .andExpect(jsonPath("$.tenants[1].slug").value("beta"))
                .andExpect(jsonPath("$.active_tenant_id").value(ACME_ID.toString()));
    }

    // ------------------------------------------------------------------ 4)
    @Test
    void responseCarriesUserIdentityAndScope() throws Exception {
        givenCurrentUserStub(List.of(acmeCard()), ACME_ID,
                Set.of("HRLAB_SUPER_ADMIN"),
                Set.of("PROJECT_READ", "POSITION_READ"));

        mvc.perform(get("/api/v1/users/me").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.user.email").value("admin@acme.dev"))
                .andExpect(jsonPath("$.user.full_name").value("ACME Admin"))
                .andExpect(jsonPath("$.roles[0]").value("HRLAB_SUPER_ADMIN"))
                .andExpect(jsonPath("$.permissions.length()").value(2))
                .andExpect(jsonPath("$.salary_data_permission").value(false));
    }

    // ------------------------------------------------------------------ 5)
    @Test
    void responseOmitsActiveTenantWhenAbsent() throws Exception {
        givenCurrentUserStub(List.of(acmeCard(), betaCard()), null,
                Set.of(), Set.of());

        mvc.perform(get("/api/v1/users/me").with(jwt()))
                .andExpect(status().isOk())
                // @JsonInclude(NON_NULL) means active_tenant_id is omitted entirely.
                .andExpect(jsonPath("$.active_tenant_id").doesNotExist())
                .andExpect(jsonPath("$.tenants.length()").value(2));
    }

    // ---------- helpers ----------

    private void givenCurrentUserStub(List<TenantMembershipSummary> tenants,
                                      UUID activeTenantId,
                                      Set<String> roles,
                                      Set<String> permissions) {
        CurrentUserResponse stub = new CurrentUserResponse(
                new CurrentUserResponse.UserIdentity(
                        USER_ID,
                        "admin@acme.dev",
                        "ACME Admin",
                        "ru-RU",
                        "ACTIVE"),
                tenants,
                activeTenantId,
                roles,
                permissions,
                false
        );
        given(getCurrentUserUseCase.currentUser()).willReturn(stub);
    }

    private static TenantMembershipSummary acmeCard() {
        return new TenantMembershipSummary(ACME_ID, "acme", "ACME", 215,
                "ACTIVE", false);
    }

    private static TenantMembershipSummary betaCard() {
        return new TenantMembershipSummary(BETA_ID, "beta", "BetaU", 145,
                "ACTIVE", false);
    }
}
