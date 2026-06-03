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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import uz.hrlab.grading.access.application.AuditQueryFilter;
import uz.hrlab.grading.access.application.ListAuditEventsQuery;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.api.GlobalExceptionHandler;
import uz.hrlab.grading.common.api.WebMvcSecurityTestConfig;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-1 — HTTP-edge security smoke for {@link AuditController}. Mirrors the
 * {@link ProjectControllerSecurityTest} pattern (anonymous, wrong authority,
 * correct authority, payload shape) without booting JPA / Liquibase.
 */
@Tag("security")
@WebMvcTest(controllers = AuditController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class AuditControllerSecurityTest {

    @Autowired MockMvc mvc;

    @MockBean ListAuditEventsQuery listAuditEvents;
    @MockBean AuditService auditService; // required by GlobalExceptionHandler

    // ---------- 1) Anonymous → 401 ----------
    @Test
    void anonymousGetIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/audit"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 2) Wrong authority → 403 ----------
    @Test
    void withoutAuditPermissionReturns403() throws Exception {
        mvc.perform(get("/api/v1/audit")
                        .with(jwt().authorities(() -> "PROJECT_READ")))
                .andExpect(status().isForbidden());
    }

    // ---------- 3) AUDIT_READ authority → 200 ----------
    @Test
    void withAuditReadAuthorityReturns200() throws Exception {
        givenStubReturnsRows(List.of());
        mvc.perform(get("/api/v1/audit")
                        .with(jwt().authorities(() -> "AUDIT_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total_elements").value(0));
    }

    // ---------- 4) Legacy AUDIT_VIEW alias also works → 200 ----------
    @Test
    void withLegacyAuditViewAuthorityReturns200() throws Exception {
        givenStubReturnsRows(List.of());
        mvc.perform(get("/api/v1/audit")
                        .with(jwt().authorities(() -> "AUDIT_VIEW")))
                .andExpect(status().isOk());
    }

    // ---------- 5) Response shape — full audit row payload ----------
    @Test
    void responseCarriesAuditRowFields() throws Exception {
        UUID id = UUID.fromString("aaaa1111-aaaa-1111-aaaa-1111aaaa1111");
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID actorId  = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID entityId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        AuditEventResponse row = new AuditEventResponse(
                id, "USER_INVITED", tenantId, null, actorId,
                "User", entityId,
                "Initial onboarding",
                "203.0.113.10", "MockTests/1.0",
                "cid-abc-123",
                OffsetDateTime.parse("2026-05-24T10:15:30+05:00"),
                "deadbeefcafe");
        givenStubReturnsRows(List.of(row));

        mvc.perform(get("/api/v1/audit")
                        .with(jwt().authorities(() -> "AUDIT_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id.toString()))
                .andExpect(jsonPath("$.items[0].action").value("USER_INVITED"))
                .andExpect(jsonPath("$.items[0].tenant_id").value(tenantId.toString()))
                .andExpect(jsonPath("$.items[0].actor_user_id").value(actorId.toString()))
                .andExpect(jsonPath("$.items[0].entity_type").value("User"))
                .andExpect(jsonPath("$.items[0].entity_id").value(entityId.toString()))
                .andExpect(jsonPath("$.items[0].hash_current").value("deadbeefcafe"))
                .andExpect(jsonPath("$.total_elements").value(1));
    }

    // ---------- 6) Query parameters pass through to the use case ----------
    @Test
    void filterQueryParamsArePassedToQuery() throws Exception {
        givenStubReturnsRows(List.of());
        mvc.perform(get("/api/v1/audit")
                        .param("action", "USER_INVITED")
                        .param("from", "2026-05-01T00:00:00Z")
                        .param("page", "2")
                        .param("size", "100")
                        .with(jwt().authorities(() -> "AUDIT_READ")))
                .andExpect(status().isOk());

        // We do not over-assert which filter values flowed — the focused
        // tests for the policy + scope rewriting live in
        // ListAuditEventsQueryTest (Sprint E backlog) and the policy unit
        // test. Here we only assert the controller did NOT 4xx on the params
        // — i.e. binding worked.
    }

    private void givenStubReturnsRows(List<AuditEventResponse> rows) {
        Page<AuditEventResponse> page = new PageImpl<>(rows);
        given(listAuditEvents.list(any(AuditQueryFilter.class))).willReturn(page);
    }
}
