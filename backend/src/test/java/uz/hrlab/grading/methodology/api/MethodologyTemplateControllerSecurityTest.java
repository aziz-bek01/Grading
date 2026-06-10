package uz.hrlab.grading.methodology.api;

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
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.api.GlobalExceptionHandler;
import uz.hrlab.grading.common.api.WebMvcSecurityTestConfig;
import uz.hrlab.grading.methodology.application.MethodologyTemplateRegistry;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice B1 — read-only methodology-template catalog endpoint.
 *
 * <p>Offline {@code @WebMvcTest} (no Docker/Testcontainers): asserts the
 * METHODOLOGY_READ gate AND the {@code { items: [...] }} envelope shape with the
 * derived {@code factor_count} / {@code default_scoring_mode} fields the FE
 * picker ({@code methodologyApi.fetchMethodologyTemplates}) consumes. Uses the
 * REAL {@link MethodologyTemplateRegistry} (in-memory, no beans pulled in) so the
 * test also locks the built-in catalog contract.
 */
@Tag("security")
@WebMvcTest(controllers = {MethodologyTemplateController.class},
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class,
        MethodologyTemplateRegistry.class})
class MethodologyTemplateControllerSecurityTest {

    @Autowired MockMvc mvc;

    // GlobalExceptionHandler needs an AuditService collaborator.
    @MockBean AuditService auditService;

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/methodology-templates"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutMethodologyReadIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/methodology-templates")
                        .with(jwt().authorities(() -> "ORG_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void withMethodologyReadReturnsCatalogEnvelope() throws Exception {
        mvc.perform(get("/api/v1/methodology-templates")
                        .with(jwt().authorities(() -> "METHODOLOGY_READ")))
                .andExpect(status().isOk())
                // Envelope shape the FE expects: { items: [...] }.
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(3))
                // Stable ordering + derived/snake_case fields on the first row.
                .andExpect(jsonPath("$.items[0].code").value("CLASSIC_8_FACTOR"))
                .andExpect(jsonPath("$.items[0].name_i18n['ru-RU']").exists())
                .andExpect(jsonPath("$.items[0].factor_count").value(8))
                .andExpect(jsonPath("$.items[0].default_scoring_mode").value("DIRECT_POINTS"))
                // Extended template carries its 11-criteria count + weighted mode.
                .andExpect(jsonPath("$.items[1].code").value("EXTENDED_11_CRITERIA"))
                .andExpect(jsonPath("$.items[1].factor_count").value(11))
                .andExpect(jsonPath("$.items[1].default_scoring_mode").value("WEIGHTED_POINTS"))
                // CUSTOM is an empty skeleton (factor_count 0) — FE routes it
                // through create-from-scratch, not from-template.
                .andExpect(jsonPath("$.items[2].code").value("CUSTOM"))
                .andExpect(jsonPath("$.items[2].factor_count").value(0));
    }
}
