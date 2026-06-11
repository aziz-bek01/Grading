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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.api.GlobalExceptionHandler;
import uz.hrlab.grading.common.api.WebMvcSecurityTestConfig;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.methodology.application.ArchiveMethodologyUseCase;
import uz.hrlab.grading.methodology.application.CreateMethodologyFromScratchUseCase;
import uz.hrlab.grading.methodology.application.CreateMethodologyFromTemplateUseCase;
import uz.hrlab.grading.methodology.application.MethodologyActorNameResolver;
import uz.hrlab.grading.methodology.application.MethodologyAggregate;
import uz.hrlab.grading.methodology.application.MethodologyQueries;
import uz.hrlab.grading.methodology.application.SaveAsTemplateUseCase;
import uz.hrlab.grading.methodology.application.UpdateMethodologyMetadataUseCase;
import uz.hrlab.grading.methodology.domain.Methodology;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("security")
@WebMvcTest(controllers = {MethodologyController.class},
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class MethodologyControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean CreateMethodologyFromTemplateUseCase createFromTemplate;
    @MockBean CreateMethodologyFromScratchUseCase createFromScratch;
    @MockBean UpdateMethodologyMetadataUseCase updateMetadata;
    @MockBean ArchiveMethodologyUseCase archiveUseCase;
    @MockBean SaveAsTemplateUseCase saveAsTemplate;
    @MockBean MethodologyQueries queries;
    @MockBean MethodologyActorNameResolver actorNames;
    @MockBean AuditService auditService;

    @Test
    void anonymousListIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/methodologies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listWithoutReadIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/methodologies")
                        .with(jwt().authorities(() -> "ORG_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listWithReadReturns200() throws Exception {
        given(queries.findByProject(any(), any()))
                .willReturn((Page) new PageImpl<>(List.of()));
        mvc.perform(get("/api/v1/methodologies")
                        .with(jwt().authorities(() -> "METHODOLOGY_READ")))
                .andExpect(status().isOk());
    }

    @Test
    void getByIdRequiresMethodologyRead() throws Exception {
        UUID id = UUID.randomUUID();
        given(queries.findMethodologyById(id)).willReturn(sample(id));
        mvc.perform(get("/api/v1/methodologies/{id}", id)
                        .with(jwt().authorities(() -> "METHODOLOGY_READ")))
                .andExpect(status().isOk());
    }

    /**
     * B4: the single-methodology detail path must carry latest_version_id (the
     * list path already does) so the FE create-from-scratch flow can deep-link
     * into the freshly-seeded v1 editor.
     */
    @Test
    void getByIdReturnsLatestVersionId() throws Exception {
        UUID id = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        given(queries.findMethodologyById(id)).willReturn(sample(id));
        given(queries.findLatestVersionId(id)).willReturn(versionId);
        mvc.perform(get("/api/v1/methodologies/{id}", id)
                        .with(jwt().authorities(() -> "METHODOLOGY_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latest_version_id").value(versionId.toString()));
    }

    @Test
    void createRequiresMethodologyCreatePermission() throws Exception {
        mvc.perform(post("/api/v1/methodologies")
                        .with(jwt().authorities(() -> "METHODOLOGY_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scratchBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void createWithMethodologyCreateReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        given(createFromScratch.create(any())).willReturn(
                new MethodologyAggregate(sample(id), sampleVersion(id)));
        mvc.perform(post("/api/v1/methodologies")
                        .with(jwt().authorities(() -> "METHODOLOGY_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scratchBody()))
                .andExpect(status().isCreated());
    }

    @Test
    void fromTemplateRequiresMethodologyCreate() throws Exception {
        mvc.perform(post("/api/v1/methodologies/from-template")
                        .with(jwt().authorities(() -> "METHODOLOGY_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template_code\":\"CLASSIC_8_FACTOR\",\"code\":\"M1\",\"name_i18n\":{\"ru-RU\":\"X\"}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchRequiresMethodologyEdit() throws Exception {
        mvc.perform(patch("/api/v1/methodologies/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "METHODOLOGY_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    /**
     * BE-5 — the extended PATCH carries methodology_type through to the use case
     * (4-arg overload). Returns 200 with the persisted type.
     */
    @Test
    void patchAppliesMethodologyType() throws Exception {
        UUID id = UUID.randomUUID();
        given(updateMetadata.update(eq(id), any(), any(),
                eq(MethodologyType.EXTENDED_11_CRITERIA)))
                .willReturn(new Methodology(id, UUID.randomUUID(), null, "M1",
                        Map.of("ru-RU", "X"), Map.of(),
                        MethodologyType.EXTENDED_11_CRITERIA, MethodologyStatus.ACTIVE));
        mvc.perform(patch("/api/v1/methodologies/{id}", id)
                        .with(jwt().authorities(() -> "METHODOLOGY_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"methodology_type\":\"EXTENDED_11_CRITERIA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.methodology_type").value("EXTENDED_11_CRITERIA"));
    }

    /**
     * BE-4 — methodology_type change blocked when an APPROVED/LOCKED version
     * exists ⇒ ValidationException METHODOLOGY_TYPE_LOCKED ⇒ 400.
     */
    @Test
    void patchMethodologyTypeLockedReturns400() throws Exception {
        given(updateMetadata.update(any(), any(), any(), any()))
                .willThrow(new uz.hrlab.grading.common.exception.ValidationException(
                        "METHODOLOGY_TYPE_LOCKED", "type locked"));
        mvc.perform(patch("/api/v1/methodologies/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "METHODOLOGY_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"methodology_type\":\"CUSTOM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("METHODOLOGY_TYPE_LOCKED"));
    }

    @Test
    void unknownIdReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(queries.findMethodologyById(id)).willThrow(new TenantAccessDeniedException());
        mvc.perform(get("/api/v1/methodologies/{id}", id)
                        .with(jwt().authorities(() -> "METHODOLOGY_READ")))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedUuidReturns400() throws Exception {
        mvc.perform(get("/api/v1/methodologies/{id}", "not-a-uuid")
                        .with(jwt().authorities(() -> "METHODOLOGY_READ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createIgnoresTenantIdInBody() throws Exception {
        UUID id = UUID.randomUUID();
        given(createFromScratch.create(any())).willReturn(
                new MethodologyAggregate(sample(id), sampleVersion(id)));
        String body = """
                {
                  "tenantId": "00000000-0000-0000-0000-000000000001",
                  "tenant_id": "00000000-0000-0000-0000-000000000002",
                  "code": "M1",
                  "name_i18n": {"ru-RU": "X"},
                  "methodology_type": "CUSTOM",
                  "scoring_mode": "DIRECT_POINTS"
                }
                """;
        mvc.perform(post("/api/v1/methodologies")
                        .with(jwt().authorities(() -> "METHODOLOGY_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    /**
     * F-409: requesting page size 500 must be capped at MAX_PAGE_SIZE (200)
     * before the query repository is consulted.
     */
    @Test
    void listPageSizeIsCappedAt200() throws Exception {
        given(queries.findByProject(any(), any(Pageable.class)))
                .willReturn((Page) new PageImpl<>(List.of()));
        mvc.perform(get("/api/v1/methodologies?page=0&size=500")
                        .with(jwt().authorities(() -> "METHODOLOGY_READ")))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(queries).findByProject(any(), pageableCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(
                pageableCaptor.getValue().getPageSize()).isLessThanOrEqualTo(200);
    }

    /**
     * D-407: listVersions returns lockedByName + approvedByName resolved
     * from MethodologyActorNameResolver.
     */
    @Test
    void listVersionsIncludesResolvedActorNames() throws Exception {
        UUID methodologyId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        UUID lockerId = UUID.randomUUID();
        MethodologyVersionJpaEntity v = new MethodologyVersionJpaEntity(
                versionId, UUID.randomUUID(), methodologyId, 1,
                MethodologyVersionStatus.LOCKED, ScoringMode.DIRECT_POINTS,
                new BigDecimal("1000"), null);
        v.setApprovedAt(OffsetDateTime.now());
        v.setApprovedBy(approverId);
        v.setLockedAt(OffsetDateTime.now());
        v.setLockedBy(lockerId);
        given(queries.listVersions(methodologyId)).willReturn(List.of(v));
        given(actorNames.resolve(eq(approverId))).willReturn("Anvar Asqarov");
        given(actorNames.resolve(eq(lockerId))).willReturn("Bobur Mirzo");

        mvc.perform(get("/api/v1/methodologies/{id}/versions", methodologyId)
                        .with(jwt().authorities(() -> "METHODOLOGY_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].approved_by_name").value("Anvar Asqarov"))
                .andExpect(jsonPath("$[0].locked_by_name").value("Bobur Mirzo"));
    }

    private String scratchBody() {
        return """
                {
                  "code": "M1",
                  "name_i18n": {"ru-RU": "X"},
                  "methodology_type": "CUSTOM",
                  "scoring_mode": "DIRECT_POINTS"
                }
                """;
    }

    private Methodology sample(UUID id) {
        return new Methodology(id, UUID.randomUUID(), null, "M1",
                Map.of("ru-RU", "X"), Map.of(),
                MethodologyType.CUSTOM, MethodologyStatus.ACTIVE);
    }

    private MethodologyVersion sampleVersion(UUID methodologyId) {
        return new MethodologyVersion(UUID.randomUUID(), UUID.randomUUID(), methodologyId,
                1, MethodologyVersionStatus.DRAFT, ScoringMode.DIRECT_POINTS,
                BigDecimal.ZERO, null, null, null, null, null);
    }
}
