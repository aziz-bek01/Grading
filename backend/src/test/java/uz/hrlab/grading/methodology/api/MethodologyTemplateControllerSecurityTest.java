package uz.hrlab.grading.methodology.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import uz.hrlab.grading.methodology.application.ArchiveCustomTemplateUseCase;
import uz.hrlab.grading.methodology.application.SaveAsTemplateUseCase;
import uz.hrlab.grading.methodology.application.TemplateCatalog;
import uz.hrlab.grading.methodology.application.TemplateCatalogEntry;
import uz.hrlab.grading.methodology.application.TemplateSource;
import uz.hrlab.grading.methodology.application.UpdateCustomTemplateUseCase;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Epic E — unified methodology-template catalog + tenant CUSTOM template
 * management endpoint security/contract (offline {@code @WebMvcTest}, no Docker).
 *
 * <p>Asserts:
 * <ul>
 *   <li>{@code GET} catalog: METHODOLOGY_READ gate + the {@code { items: [...] }}
 *       envelope carrying built-ins (is_builtin=true, id=null) UNION the tenant's
 *       custom templates (is_builtin=false, id present, source=CUSTOM);</li>
 *   <li>{@code POST} save-as-template: METHODOLOGY_CREATE gate;</li>
 *   <li>{@code PUT} rename + {@code DELETE} archive: METHODOLOGY_EDIT gate.</li>
 * </ul>
 * The {@link TemplateCatalog} + manage use cases are mocked; the tenant context
 * is seeded directly (the real TenantContextFilter is excluded in this slice).
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
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class MethodologyTemplateControllerSecurityTest {

    @Autowired MockMvc mvc;

    @MockBean TemplateCatalog templateCatalog;
    @MockBean SaveAsTemplateUseCase saveAsTemplate;
    @MockBean UpdateCustomTemplateUseCase updateCustomTemplate;
    @MockBean ArchiveCustomTemplateUseCase archiveCustomTemplate;
    // GlobalExceptionHandler needs an AuditService collaborator.
    @MockBean AuditService auditService;

    private static final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // list() resolves the tenant from TenantContext; seed it for this slice.
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), TENANT, Set.of(), Set.of(),
                Set.of("METHODOLOGY_READ"), Set.of(), false, "ru-RU"));
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

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
    void catalogUnionsBuiltinsAndTenantCustom() throws Exception {
        UUID customId = UUID.randomUUID();
        given(templateCatalog.list(TENANT)).willReturn(List.of(
                new TemplateCatalogEntry(null, "CLASSIC_8_FACTOR",
                        TemplateSource.BUILTIN, true,
                        Map.of("ru-RU", "Классическая 8-факторная"), Map.of(),
                        MethodologyType.CLASSIC_8_FACTOR, ScoringMode.DIRECT_POINTS, 8),
                new TemplateCatalogEntry(customId, "BANK_GRADING_2026",
                        TemplateSource.CUSTOM, false,
                        Map.of("ru-RU", "Грейдинг банка"), Map.of("ru-RU", "Кастомный"),
                        MethodologyType.CUSTOM, ScoringMode.WEIGHTED_POINTS, 11)));

        mvc.perform(get("/api/v1/methodology-templates")
                        .with(jwt().authorities(() -> "METHODOLOGY_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                // Built-in: read-only, no id, source flag.
                .andExpect(jsonPath("$.items[0].code").value("CLASSIC_8_FACTOR"))
                .andExpect(jsonPath("$.items[0].is_builtin").value(true))
                .andExpect(jsonPath("$.items[0].source").value("BUILTIN"))
                .andExpect(jsonPath("$.items[0].id").doesNotExist())
                .andExpect(jsonPath("$.items[0].factor_count").value(8))
                .andExpect(jsonPath("$.items[0].default_scoring_mode").value("DIRECT_POINTS"))
                // Custom: carries the row id so the FE can target manage endpoints.
                .andExpect(jsonPath("$.items[1].code").value("BANK_GRADING_2026"))
                .andExpect(jsonPath("$.items[1].is_builtin").value(false))
                .andExpect(jsonPath("$.items[1].source").value("CUSTOM"))
                .andExpect(jsonPath("$.items[1].id").value(customId.toString()))
                .andExpect(jsonPath("$.items[1].factor_count").value(11));
    }

    @Test
    void saveAsTemplateRequiresMethodologyCreate() throws Exception {
        mvc.perform(post("/api/v1/methodology-templates")
                        .with(jwt().authorities(() -> "METHODOLOGY_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source_version_id\":\"" + UUID.randomUUID()
                                + "\",\"code\":\"T1\",\"name_i18n\":{\"ru-RU\":\"X\"}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void saveAsTemplateWithCreateReturns201() throws Exception {
        given(saveAsTemplate.saveAsTemplate(any())).willReturn(UUID.randomUUID());
        mvc.perform(post("/api/v1/methodology-templates")
                        .with(jwt().authorities(() -> "METHODOLOGY_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source_version_id\":\"" + UUID.randomUUID()
                                + "\",\"code\":\"T1\",\"name_i18n\":{\"ru-RU\":\"X\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.template_id").exists());
    }

    @Test
    void renameRequiresMethodologyEdit() throws Exception {
        mvc.perform(put("/api/v1/methodology-templates/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "METHODOLOGY_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name_i18n\":{\"ru-RU\":\"Y\"}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void archiveRequiresMethodologyEdit() throws Exception {
        mvc.perform(delete("/api/v1/methodology-templates/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "METHODOLOGY_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void archiveWithEditReturns204() throws Exception {
        mvc.perform(delete("/api/v1/methodology-templates/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "METHODOLOGY_EDIT")))
                .andExpect(status().isNoContent());
    }
}
