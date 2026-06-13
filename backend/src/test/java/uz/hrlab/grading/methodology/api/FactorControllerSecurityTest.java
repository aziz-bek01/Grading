package uz.hrlab.grading.methodology.api;

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
import uz.hrlab.grading.methodology.application.FactorLevelService;
import uz.hrlab.grading.methodology.application.FactorService;
import uz.hrlab.grading.methodology.application.MethodologyQueries;
import uz.hrlab.grading.methodology.domain.Factor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("security")
@WebMvcTest(controllers = {FactorController.class},
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class FactorControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean FactorService factorService;
    @MockBean FactorLevelService levelService;
    @MockBean MethodologyQueries queries;
    @MockBean AuditService auditService;

    @Test
    void anonymousGetIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/factors/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getWithoutReadIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/factors/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "ORG_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchRequiresEdit() throws Exception {
        mvc.perform(patch("/api/v1/factors/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "METHODOLOGY_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteRequiresEdit() throws Exception {
        mvc.perform(delete("/api/v1/factors/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "METHODOLOGY_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteLevelRequiresEdit() throws Exception {
        mvc.perform(delete("/api/v1/factor-levels/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "METHODOLOGY_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownFactorIdReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(queries.findFactorById(id)).willThrow(new TenantAccessDeniedException());
        mvc.perform(get("/api/v1/factors/{id}", id)
                        .with(jwt().authorities(() -> "METHODOLOGY_READ")))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedFactorUuidReturns400() throws Exception {
        mvc.perform(get("/api/v1/factors/{id}", "not-a-uuid")
                        .with(jwt().authorities(() -> "METHODOLOGY_READ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchAcceptedWithMethodologyEditApprovedAuthority() throws Exception {
        // BE-2 — the widened coarse @PreAuthorize lets METHODOLOGY_EDIT_APPROVED
        // through (the fine status decision lives in the service). The service is
        // mocked, so a non-403 (here 200) proves the gate admitted the request.
        UUID id = UUID.randomUUID();
        given(factorService.update(org.mockito.ArgumentMatchers.eq(id),
                org.mockito.ArgumentMatchers.any()))
                .willReturn(new Factor(id, UUID.randomUUID(), UUID.randomUUID(), "EDU",
                        Map.of("ru-RU", "Образование"), Map.of(),
                        new BigDecimal("100"), new BigDecimal("100"), 1, true));
        mvc.perform(patch("/api/v1/factors/{id}", id)
                        .with(jwt().authorities(() -> "METHODOLOGY_EDIT_APPROVED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void getFactorWithReadReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(queries.findFactorById(id)).willReturn(
                new Factor(id, UUID.randomUUID(), UUID.randomUUID(), "EDU",
                        Map.of("ru-RU", "Образование"), Map.of(),
                        new BigDecimal("100"), new BigDecimal("100"), 1, true));
        mvc.perform(get("/api/v1/factors/{id}", id)
                        .with(jwt().authorities(() -> "METHODOLOGY_READ")))
                .andExpect(status().isOk());
    }
}
