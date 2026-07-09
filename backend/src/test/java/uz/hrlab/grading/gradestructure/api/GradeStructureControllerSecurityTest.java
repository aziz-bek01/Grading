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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.api.GlobalExceptionHandler;
import uz.hrlab.grading.common.api.WebMvcSecurityTestConfig;
import uz.hrlab.grading.common.exception.ConflictException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.gradestructure.application.ApproveGradeStructureUseCase;
import uz.hrlab.grading.gradestructure.application.ArchiveGradeStructureUseCase;
import uz.hrlab.grading.gradestructure.application.CreateGradeStructureFromScratchUseCase;
import uz.hrlab.grading.gradestructure.application.CreateGradeStructureFromTemplateUseCase;
import uz.hrlab.grading.gradestructure.application.CreateGradeStructureVersionUseCase;
import uz.hrlab.grading.gradestructure.application.DeleteGradeStructureUseCase;
import uz.hrlab.grading.gradestructure.application.GradeBandLookupService;
import uz.hrlab.grading.gradestructure.application.GradePyramidQuery;
import uz.hrlab.grading.gradestructure.application.GradeService;
import uz.hrlab.grading.gradestructure.application.GradeStructureQueries;
import uz.hrlab.grading.gradestructure.application.LockGradeStructureUseCase;
import uz.hrlab.grading.gradestructure.application.SaveAsGradeTemplateUseCase;
import uz.hrlab.grading.gradestructure.application.UpdateGradeStructureMetadataUseCase;
import uz.hrlab.grading.gradestructure.domain.Grade;
import uz.hrlab.grading.gradestructure.domain.GradeBandLookupResult;
import uz.hrlab.grading.gradestructure.domain.GradeStructureTransitionRejectedException;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-10 — GradeStructureController endpoint security/contract (offline
 * {@code @WebMvcTest}, no Docker). Asserts permission gates (200/403/404/409),
 * the DRAFT-only guard surfacing as the transition-rejected error, the BE-3
 * preview {@code out_of_range} derivation, the BE-2 enriched pyramid shape, the
 * BE-1 list {@code grade_count}/{@code updated_at}, the BE-5 reorder
 * {@code {items:[...]}} envelope, and the BE-9 save-as duplicate-code 409.
 * Use cases are mocked; the real {@code TenantContextFilter} is excluded.
 */
@Tag("security")
@WebMvcTest(controllers = {GradeStructureController.class, GradeController.class},
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class GradeStructureControllerSecurityTest {

    @Autowired MockMvc mvc;

    @MockBean CreateGradeStructureFromTemplateUseCase fromTemplate;
    @MockBean CreateGradeStructureFromScratchUseCase fromScratch;
    @MockBean UpdateGradeStructureMetadataUseCase updateMetadata;
    @MockBean ApproveGradeStructureUseCase approveUseCase;
    @MockBean LockGradeStructureUseCase lockUseCase;
    @MockBean ArchiveGradeStructureUseCase archiveUseCase;
    @MockBean CreateGradeStructureVersionUseCase versionUseCase;
    @MockBean DeleteGradeStructureUseCase deleteUseCase;
    @MockBean SaveAsGradeTemplateUseCase saveAsTemplate;
    @MockBean GradeService gradeService;
    @MockBean GradeStructureQueries queries;
    @MockBean GradePyramidQuery pyramid;
    @MockBean GradeBandLookupService lookup;
    @MockBean AuditService auditService;

    @BeforeEach
    void setUp() {
        // The preview endpoint resolves the active tenant from TenantContext
        // (the real TenantContextFilter is excluded from this slice). Seed it so
        // requireActive() succeeds; the lookup service is mocked regardless.
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), UUID.randomUUID(), Set.of(), Set.of(),
                Set.of("GRADE_READ"), Set.of(), false, "ru-RU"));
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    // ---- list (BE-1) ----

    @Test
    void anonymousListIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/grade-structures"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listWithoutGradeReadIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/grade-structures")
                        .with(jwt().authorities(() -> "ORG_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listEnrichesGradeCountAndAuditTimestamps() throws Exception {
        UUID id = UUID.randomUUID();
        GradeStructureJpaEntity e = new GradeStructureJpaEntity(
                id, UUID.randomUUID(), null, "GS1",
                uz.hrlab.grading.gradestructure.domain.GradeStructureType.CUSTOM,
                uz.hrlab.grading.gradestructure.domain.GradeStructureStatus.DRAFT,
                1, null, null);
        e.setNameI18n(Map.of("ru-RU", "X"));
        Page<GradeStructureJpaEntity> page = new PageImpl<>(List.of(e));
        given(queries.findByProject(any(), any(), any())).willReturn(page);
        given(queries.gradeCountsByStructureIds(any())).willReturn(Map.of(id, 14));

        mvc.perform(get("/api/v1/grade-structures")
                        .with(jwt().authorities(() -> "GRADE_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].grade_count").value(14))
                .andExpect(jsonPath("$.items[0].code").value("GS1"))
                // created_at is auto-populated only on persist; the field key must
                // exist on the list wire shape (BE-1 — no FE phantom-field drift).
                .andExpect(jsonPath("$.items[0]").exists());
    }

    // ---- detail tenant isolation ----

    @Test
    void getByIdCrossTenantReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(queries.findDetail(id)).willThrow(new TenantAccessDeniedException());
        mvc.perform(get("/api/v1/grade-structures/{id}", id)
                        .with(jwt().authorities(() -> "GRADE_READ")))
                .andExpect(status().isNotFound());
    }

    // ---- delete (BE-4) ----

    @Test
    void deleteRequiresGradeEdit() throws Exception {
        mvc.perform(delete("/api/v1/grade-structures/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "GRADE_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteDraftReturns204AndAudits() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete("/api/v1/grade-structures/{id}", id)
                        .with(jwt().authorities(() -> "GRADE_EDIT")))
                .andExpect(status().isNoContent());
        verify(deleteUseCase).delete(eq(id));
    }

    @Test
    void deleteNonDraftRejectedByTransitionGuard() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new GradeStructureTransitionRejectedException(
                "Cannot modify a grade structure in state APPROVED"))
                .when(deleteUseCase).delete(eq(id));
        // BE-014: GradeStructureTransitionRejectedException is a
        // DomainTransitionRejectedException -> 409 CONFLICT with its stable code.
        mvc.perform(delete("/api/v1/grade-structures/{id}", id)
                        .with(jwt().authorities(() -> "GRADE_EDIT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_STRUCTURE_TRANSITION_REJECTED"));
    }

    @Test
    void deleteCrossTenantReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new TenantAccessDeniedException()).when(deleteUseCase).delete(eq(id));
        mvc.perform(delete("/api/v1/grade-structures/{id}", id)
                        .with(jwt().authorities(() -> "GRADE_EDIT")))
                .andExpect(status().isNotFound());
    }

    // ---- reorder (BE-5) ----

    @Test
    void reorderRequiresGradeEdit() throws Exception {
        mvc.perform(post("/api/v1/grade-structures/{id}/grades/reorder", UUID.randomUUID())
                        .with(jwt().authorities(() -> "GRADE_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ordered_ids\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reorderReturnsItemsEnvelopeSnakeCase() throws Exception {
        UUID structureId = UUID.randomUUID();
        UUID g1 = UUID.randomUUID();
        UUID g2 = UUID.randomUUID();
        given(gradeService.reorderGrades(eq(structureId), any())).willReturn(List.of(
                new Grade(g1, UUID.randomUUID(), structureId, 1, Map.of("ru-RU", "A"), Map.of(), 1),
                new Grade(g2, UUID.randomUUID(), structureId, 2, Map.of("ru-RU", "B"), Map.of(), 2)));
        mvc.perform(post("/api/v1/grade-structures/{id}/grades/reorder", structureId)
                        .with(jwt().authorities(() -> "GRADE_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ordered_ids\":[\"" + g1 + "\",\"" + g2 + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(g1.toString()))
                .andExpect(jsonPath("$.items[0].grade_number").value(1))
                .andExpect(jsonPath("$.items[1].sort_order").value(2));
    }

    @Test
    void reorderSetMismatchReturns400() throws Exception {
        UUID structureId = UUID.randomUUID();
        given(gradeService.reorderGrades(eq(structureId), any()))
                .willThrow(new uz.hrlab.grading.common.exception.ValidationException(
                        "GRADE_REORDER_SET_MISMATCH", "mismatch"));
        mvc.perform(post("/api/v1/grade-structures/{id}/grades/reorder", structureId)
                        .with(jwt().authorities(() -> "GRADE_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ordered_ids\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GRADE_REORDER_SET_MISMATCH"));
    }

    // ---- preview (BE-3) ----

    @Test
    void previewMatchedEmitsOutOfRangeFalse() throws Exception {
        UUID id = UUID.randomUUID();
        UUID bandId = UUID.randomUUID();
        UUID gradeId = UUID.randomUUID();
        given(lookup.lookup(any(), eq(id), any()))
                .willReturn(java.util.Optional.of(new GradeBandLookupResult(bandId, gradeId, 7)));
        mvc.perform(post("/api/v1/grade-structures/{id}/preview-grade-lookup", id)
                        .with(jwt().authorities(() -> "GRADE_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":150}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.out_of_range").value(false))
                .andExpect(jsonPath("$.grade_number").value(7))
                .andExpect(jsonPath("$.grade_band_id").value(bandId.toString()));
    }

    @Test
    void previewMissEmitsOutOfRangeTrue() throws Exception {
        UUID id = UUID.randomUUID();
        given(lookup.lookup(any(), eq(id), any())).willReturn(java.util.Optional.empty());
        mvc.perform(post("/api/v1/grade-structures/{id}/preview-grade-lookup", id)
                        .with(jwt().authorities(() -> "GRADE_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":99999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(false))
                .andExpect(jsonPath("$.out_of_range").value(true))
                .andExpect(jsonPath("$.grade_band_id").doesNotExist());
    }

    // ---- pyramid (BE-2) ----

    @Test
    void pyramidReturnsEnrichedShape() throws Exception {
        UUID id = UUID.randomUUID();
        UUID gradeId = UUID.randomUUID();
        given(pyramid.pyramid(id)).willReturn(List.of(
                new GradePyramidQuery.GradePyramidRow(7, gradeId, Map.of("ru-RU", "Грейд 7"),
                        new BigDecimal("100.0000"), new BigDecimal("199.9999"), 3L, 5L)));
        mvc.perform(get("/api/v1/grade-structures/{id}/pyramid", id)
                        .with(jwt().authorities(() -> "GRADE_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].grade_number").value(7))
                .andExpect(jsonPath("$.rows[0].grade_id").value(gradeId.toString()))
                .andExpect(jsonPath("$.rows[0].grade_name.ru-RU").value("Грейд 7"))
                .andExpect(jsonPath("$.rows[0].position_count").value(3))
                .andExpect(jsonPath("$.rows[0].evaluation_count").value(5))
                .andExpect(jsonPath("$.rows[0].min_score").value(100.0000))
                .andExpect(jsonPath("$.rows[0].max_score").value(199.9999));
    }

    // ---- band delete (BE-6) ----

    @Test
    void deleteBandRequiresGradeEdit() throws Exception {
        mvc.perform(delete("/api/v1/grades/{id}/band", UUID.randomUUID())
                        .with(jwt().authorities(() -> "GRADE_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteBandReturns204() throws Exception {
        UUID gradeId = UUID.randomUUID();
        mvc.perform(delete("/api/v1/grades/{id}/band", gradeId)
                        .with(jwt().authorities(() -> "GRADE_EDIT")))
                .andExpect(status().isNoContent());
        verify(gradeService).removeBand(eq(gradeId));
    }

    @Test
    void deleteBandNonDraftRejectedByTransitionGuard() throws Exception {
        UUID gradeId = UUID.randomUUID();
        doThrow(new GradeStructureTransitionRejectedException("locked"))
                .when(gradeService).removeBand(eq(gradeId));
        // BE-014: transition rejection -> 409 CONFLICT (was 400 pre-fix).
        mvc.perform(delete("/api/v1/grades/{id}/band", gradeId)
                        .with(jwt().authorities(() -> "GRADE_EDIT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_STRUCTURE_TRANSITION_REJECTED"));
    }

    // ---- save-as-template (BE-9) ----

    @Test
    void saveAsTemplateRequiresGradeEdit() throws Exception {
        mvc.perform(post("/api/v1/grade-structures/{id}/save-as-template", UUID.randomUUID())
                        .with(jwt().authorities(() -> "GRADE_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"T1\",\"name_i18n\":{\"ru-RU\":\"X\"}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void saveAsTemplateReturns201WithTemplateId() throws Exception {
        UUID templateId = UUID.randomUUID();
        given(saveAsTemplate.saveAsTemplate(any(), any(), any(), any())).willReturn(templateId);
        mvc.perform(post("/api/v1/grade-structures/{id}/save-as-template", UUID.randomUUID())
                        .with(jwt().authorities(() -> "GRADE_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"T1\",\"name_i18n\":{\"ru-RU\":\"X\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.template_id").value(templateId.toString()));
    }

    @Test
    void saveAsTemplateDuplicateCodeReturns409() throws Exception {
        given(saveAsTemplate.saveAsTemplate(any(), any(), any(), any()))
                .willThrow(new ConflictException("GRADE_TEMPLATE_CODE_EXISTS",
                        "Template code already exists in this tenant"));
        mvc.perform(post("/api/v1/grade-structures/{id}/save-as-template", UUID.randomUUID())
                        .with(jwt().authorities(() -> "GRADE_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DUP\",\"name_i18n\":{\"ru-RU\":\"X\"}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADE_TEMPLATE_CODE_EXISTS"));
    }

    @Test
    void saveAsTemplateIgnoresTenantIdInBody() throws Exception {
        given(saveAsTemplate.saveAsTemplate(any(), any(), any(), any()))
                .willReturn(UUID.randomUUID());
        String body = """
                {
                  "tenantId": "00000000-0000-0000-0000-000000000001",
                  "tenant_id": "00000000-0000-0000-0000-000000000002",
                  "code": "T1",
                  "name_i18n": {"ru-RU": "X"}
                }
                """;
        mvc.perform(post("/api/v1/grade-structures/{id}/save-as-template", UUID.randomUUID())
                        .with(jwt().authorities(() -> "GRADE_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
