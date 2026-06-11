package uz.hrlab.grading.gradestructure.api;

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
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.gradestructure.application.ArchiveGradeTemplateUseCase;
import uz.hrlab.grading.gradestructure.application.GradeTemplateCatalog;
import uz.hrlab.grading.gradestructure.application.GradeTemplateCatalogEntry;
import uz.hrlab.grading.gradestructure.application.GradeTemplateSource;
import uz.hrlab.grading.gradestructure.application.UpdateGradeTemplateUseCase;
import uz.hrlab.grading.gradestructure.domain.GradeStructureType;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-10 — unified grade-template catalog + tenant CUSTOM template management
 * endpoint security/contract (offline {@code @WebMvcTest}, no Docker). Mirrors
 * {@code MethodologyTemplateControllerSecurityTest}.
 */
@Tag("security")
@WebMvcTest(controllers = {GradeStructureTemplateController.class},
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class GradeStructureTemplateControllerSecurityTest {

    @Autowired MockMvc mvc;

    @MockBean GradeTemplateCatalog templateCatalog;
    @MockBean UpdateGradeTemplateUseCase updateTemplate;
    @MockBean ArchiveGradeTemplateUseCase archiveTemplate;
    @MockBean AuditService auditService;

    private static final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // list() resolves the tenant from TenantContext; seed it for this slice.
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), TENANT, Set.of(), Set.of(),
                Set.of("GRADE_READ"), Set.of(), false, "ru-RU"));
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/grade-structure-templates"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutGradeReadIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/grade-structure-templates")
                        .with(jwt().authorities(() -> "ORG_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogUnionsBuiltinsAndTenantCustom() throws Exception {
        UUID customId = UUID.randomUUID();
        given(templateCatalog.list(TENANT)).willReturn(List.of(
                new GradeTemplateCatalogEntry(null, "GRADE_14",
                        GradeTemplateSource.BUILTIN, true,
                        Map.of("ru-RU", "14-грейдная"), Map.of(),
                        GradeStructureType.GRADE_14, 14),
                new GradeTemplateCatalogEntry(customId, "BANK_GRADES_2026",
                        GradeTemplateSource.CUSTOM, false,
                        Map.of("ru-RU", "Грейды банка"), Map.of("ru-RU", "Кастом"),
                        GradeStructureType.CUSTOM, 9)));

        mvc.perform(get("/api/v1/grade-structure-templates")
                        .with(jwt().authorities(() -> "GRADE_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].code").value("GRADE_14"))
                .andExpect(jsonPath("$.items[0].is_builtin").value(true))
                .andExpect(jsonPath("$.items[0].source").value("BUILTIN"))
                .andExpect(jsonPath("$.items[0].id").doesNotExist())
                .andExpect(jsonPath("$.items[0].grade_count").value(14))
                .andExpect(jsonPath("$.items[0].structure_type").value("GRADE_14"))
                .andExpect(jsonPath("$.items[1].code").value("BANK_GRADES_2026"))
                .andExpect(jsonPath("$.items[1].is_builtin").value(false))
                .andExpect(jsonPath("$.items[1].source").value("CUSTOM"))
                .andExpect(jsonPath("$.items[1].id").value(customId.toString()))
                .andExpect(jsonPath("$.items[1].grade_count").value(9));
    }

    @Test
    void renameRequiresGradeEdit() throws Exception {
        mvc.perform(put("/api/v1/grade-structure-templates/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "GRADE_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name_i18n\":{\"ru-RU\":\"Y\"}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void renameWithEditReturnsTemplateId() throws Exception {
        UUID id = UUID.randomUUID();
        given(updateTemplate.rename(eq(id), any(), any())).willReturn(id);
        mvc.perform(put("/api/v1/grade-structure-templates/{id}", id)
                        .with(jwt().authorities(() -> "GRADE_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name_i18n\":{\"ru-RU\":\"Y\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.template_id").value(id.toString()));
    }

    @Test
    void renameCrossTenantReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(updateTemplate.rename(eq(id), any(), any()))
                .willThrow(new TenantAccessDeniedException());
        mvc.perform(put("/api/v1/grade-structure-templates/{id}", id)
                        .with(jwt().authorities(() -> "GRADE_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name_i18n\":{\"ru-RU\":\"Y\"}}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void archiveRequiresGradeEdit() throws Exception {
        mvc.perform(delete("/api/v1/grade-structure-templates/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "GRADE_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void archiveWithEditReturns204() throws Exception {
        mvc.perform(delete("/api/v1/grade-structure-templates/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "GRADE_EDIT")))
                .andExpect(status().isNoContent());
    }

    @Test
    void archiveCrossTenantReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new TenantAccessDeniedException()).when(archiveTemplate).archive(eq(id));
        mvc.perform(delete("/api/v1/grade-structure-templates/{id}", id)
                        .with(jwt().authorities(() -> "GRADE_EDIT")))
                .andExpect(status().isNotFound());
    }
}
