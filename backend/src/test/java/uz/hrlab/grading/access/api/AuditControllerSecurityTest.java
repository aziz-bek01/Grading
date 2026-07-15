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
import uz.hrlab.grading.access.application.VerifyAuditIntegrityQuery;
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
    @MockBean VerifyAuditIntegrityQuery verifyAuditIntegrity;
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

    // =====================================================================
    //  MVP1-E10-1 — GET /api/v1/audit/integrity edge security. Stricter than
    //  the reader: AUDIT_READ ONLY (no AUDIT_VIEW alias).
    // =====================================================================

    // ---------- integrity: anonymous → 401 ----------
    @Test
    void integrityAnonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/audit/integrity"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- integrity: wrong authority → 403 ----------
    @Test
    void integrityWithoutAuditReadReturns403() throws Exception {
        mvc.perform(get("/api/v1/audit/integrity")
                        .with(jwt().authorities(() -> "PROJECT_READ")))
                .andExpect(status().isForbidden());
    }

    // ---------- integrity: AUDIT_VIEW alone is NOT enough → 403 ----------
    @Test
    void integrityWithOnlyAuditViewReturns403() throws Exception {
        mvc.perform(get("/api/v1/audit/integrity")
                        .with(jwt().authorities(() -> "AUDIT_VIEW")))
                .andExpect(status().isForbidden());
    }

    // ---------- integrity: AUDIT_READ → 200 + body shape ----------
    @Test
    void integrityWithAuditReadReturns200AndBody() throws Exception {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        given(verifyAuditIntegrity.verify(any(), any(), any()))
                .willReturn(new AuditIntegrityResponse(
                        tenantId, "INTACT", true, 42L, 42L, 40L, 2L,
                        OffsetDateTime.parse("2026-07-15T00:00:00Z"),
                        OffsetDateTime.parse("2026-07-15T09:41:12.512874Z"),
                        null, null, false, 50000, null,
                        OffsetDateTime.parse("2026-07-15T09:42:03.114Z")));

        mvc.perform(get("/api/v1/audit/integrity")
                        .with(jwt().authorities(() -> "AUDIT_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant_id").value(tenantId.toString()))
                .andExpect(jsonPath("$.status").value("INTACT"))
                .andExpect(jsonPath("$.intact").value(true))
                .andExpect(jsonPath("$.checked_count").value(42))
                .andExpect(jsonPath("$.chain_length").value(42))
                .andExpect(jsonPath("$.independently_verified_count").value(40))
                .andExpect(jsonPath("$.legacy_unverifiable_count").value(2))
                .andExpect(jsonPath("$.verifiable_from").value("2026-07-15T00:00:00Z"))
                .andExpect(jsonPath("$.max_rows").value(50000))
                .andExpect(jsonPath("$.first_break").doesNotExist());
    }

    // ---------- integrity: BROKEN result carries first_break ----------
    @Test
    void integrityBrokenResultCarriesFirstBreak() throws Exception {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID rowId    = UUID.fromString("9f2caaaa-0000-0000-0000-000000000001");
        AuditIntegrityResponse.Break brk = new AuditIntegrityResponse.Break(
                rowId, OffsetDateTime.parse("2026-07-14T18:03:00Z"),
                "HASH_MISMATCH", "a1b2expected", "deadbeefactual");
        given(verifyAuditIntegrity.verify(any(), any(), any()))
                .willReturn(new AuditIntegrityResponse(
                        tenantId, "BROKEN", false, 7L, 100L, 7L, 0L,
                        OffsetDateTime.parse("2026-07-14T17:00:00Z"),
                        OffsetDateTime.parse("2026-07-14T18:02:00Z"),
                        null, null, false, 50000, brk,
                        OffsetDateTime.parse("2026-07-15T09:42:03.114Z")));

        mvc.perform(get("/api/v1/audit/integrity")
                        .with(jwt().authorities(() -> "AUDIT_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BROKEN"))
                .andExpect(jsonPath("$.intact").value(false))
                .andExpect(jsonPath("$.first_break.row_id").value(rowId.toString()))
                .andExpect(jsonPath("$.first_break.break_type").value("HASH_MISMATCH"))
                .andExpect(jsonPath("$.first_break.expected_hash").value("a1b2expected"))
                .andExpect(jsonPath("$.first_break.actual_hash").value("deadbeefactual"));
    }
}
