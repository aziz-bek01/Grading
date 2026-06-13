package uz.hrlab.grading.position.api;

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
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.api.GlobalExceptionHandler;
import uz.hrlab.grading.common.api.WebMvcSecurityTestConfig;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.position.application.ArchivePositionUseCase;
import uz.hrlab.grading.position.application.CreatePositionUseCase;
import uz.hrlab.grading.position.application.FindPositionQuery;
import uz.hrlab.grading.position.application.UpdatePositionUseCase;
import uz.hrlab.grading.position.domain.Position;
import uz.hrlab.grading.position.domain.PositionStatus;

import java.util.List;
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
 * D-203 — HTTP-layer security smoke for {@link PositionController}. Mirrors
 * {@code ProjectControllerSecurityTest}: anonymous → 401, wrong authority →
 * 403, malformed UUID → 400, unknown id → 404, tenantId-in-body ignored,
 * archive/patch permission checks.
 */
@Tag("security")
@WebMvcTest(controllers = PositionController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class PositionControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean CreatePositionUseCase createUseCase;
    @MockBean UpdatePositionUseCase updateUseCase;
    @MockBean ArchivePositionUseCase archiveUseCase;
    @MockBean FindPositionQuery findQuery;
    @MockBean AuditService auditService; // required by GlobalExceptionHandler

    // ---------- 1) Anonymous → 401 ----------
    @Test
    void anonymousGetIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/positions").param("projectId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 2) Wrong authority → 403 ----------
    @Test
    void getWithoutPositionReadReturns403() throws Exception {
        mvc.perform(get("/api/v1/positions")
                        .param("projectId", UUID.randomUUID().toString())
                        .with(jwt().authorities(() -> "ORG_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void postWithoutPositionCreateReturns403() throws Exception {
        mvc.perform(post("/api/v1/positions")
                        .with(jwt().authorities(() -> "POSITION_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isForbidden());
    }

    // ---------- 3) Correct authority → 2xx ----------
    @Test
    void getListWithPositionReadReturns200() throws Exception {
        given(findQuery.list(any(), any(), anyBoolean(), any(), any(), anyInt(), anyInt()))
                .willReturn(new PageImpl<>(List.<Position>of()));
        mvc.perform(get("/api/v1/positions")
                        .param("projectId", UUID.randomUUID().toString())
                        .with(jwt().authorities(() -> "POSITION_READ")))
                .andExpect(status().isOk());
    }

    @Test
    void createWithPositionCreateReturns201() throws Exception {
        Position created = makePosition();
        given(createUseCase.create(any())).willReturn(created);

        mvc.perform(post("/api/v1/positions")
                        .with(jwt().authorities(() -> "POSITION_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated());
    }

    // ---------- 4) tenantId in body is ignored ----------
    @Test
    void bodyTenantIdFieldIsIgnoredByJacksonOnRecordDto() throws Exception {
        Position created = makePosition();
        given(createUseCase.create(any())).willReturn(created);

        String bodyWithSpoofedTenant = """
                {
                  "tenantId": "00000000-0000-0000-0000-000000000001",
                  "tenant_id": "00000000-0000-0000-0000-000000000002",
                  "project_id": "%s",
                  "department_id": "%s",
                  "code": "POS-1",
                  "title_i18n": {"ru-RU": "Инженер"}
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mvc.perform(post("/api/v1/positions")
                        .with(jwt().authorities(() -> "POSITION_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithSpoofedTenant))
                .andExpect(status().isCreated());
    }

    // ---------- 5) Unknown ID → 404 ----------
    @Test
    void unknownIdReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(findQuery.findById(id)).willThrow(new TenantAccessDeniedException());
        mvc.perform(get("/api/v1/positions/{id}", id)
                        .with(jwt().authorities(() -> "POSITION_READ")))
                .andExpect(status().isNotFound());
    }

    // ---------- 6) Malformed UUID → 400 ----------
    @Test
    void malformedUuidReturns400() throws Exception {
        mvc.perform(get("/api/v1/positions/{id}", "not-a-uuid")
                        .with(jwt().authorities(() -> "POSITION_READ")))
                .andExpect(status().isBadRequest());
    }

    // ---------- 7) Archive without POSITION_EDIT → 403 ----------
    @Test
    void archiveWithoutPositionEditReturns403() throws Exception {
        mvc.perform(post("/api/v1/positions/{id}/archive", UUID.randomUUID())
                        .with(jwt().authorities(() -> "POSITION_READ")))
                .andExpect(status().isForbidden());
    }

    // ---------- 8) Patch without POSITION_EDIT → 403 ----------
    @Test
    void patchWithoutPositionEditReturns403() throws Exception {
        mvc.perform(patch("/api/v1/positions/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "POSITION_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title_i18n\":{\"ru-RU\":\"X\"}}"))
                .andExpect(status().isForbidden());
    }

    private Position makePosition() {
        return new Position(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "POS-1", Map.of("ru-RU", "Инженер"),
                null, null, null, null, PositionStatus.ACTIVE);
    }

    private String validCreateBody() throws Exception {
        return json.writeValueAsString(new CreatePositionRequest(
                UUID.randomUUID(), UUID.randomUUID(), "POS-OK",
                Map.of("ru-RU", "Инженер"), null, null, null, null));
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    private static boolean anyBoolean() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }
}
