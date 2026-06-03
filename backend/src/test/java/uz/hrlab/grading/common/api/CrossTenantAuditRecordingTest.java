package uz.hrlab.grading.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.security.DevAuthentication;
import uz.hrlab.grading.security.JwtTenantContextResolver;
import uz.hrlab.grading.security.TenantContextFilter;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves that a cross-tenant access attempt produces a hash-chained audit
 * row, not just a SLF4J log line (defect D-002, finding F-05).
 *
 * <p>Uses a mocked {@link AuditService} so the test runs without
 * Testcontainers. The companion full-chain test (with a real DB) lives in
 * {@code AuditAppendOnlyTest}.
 */
@Tag("audit")
class CrossTenantAuditRecordingTest {

    @Test
    void crossTenantAccessWritesAuditEventWithCorrectAction() {
        AuditService audit = mock(AuditService.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler(audit);

        UUID actorUserId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        TenantContext ctx = new TenantContext(actorUserId, tenantId,
                Set.of(), Set.of("HRLAB_PROJECT_MANAGER"), Set.of("POSITION_READ"),
                Set.of(), false, "ru-RU");
        TenantContextHolder.set(ctx);

        try {
            HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getRequestURI()).thenReturn("/api/v1/positions/00000000-0000-0000-0000-000000000999");
            when(req.getMethod()).thenReturn("GET");
            when(req.getHeader("User-Agent")).thenReturn("Mozilla/5.0 (test)");
            when(req.getRemoteAddr()).thenReturn("10.0.0.5");

            var response = handler.handleTenantAccessDenied(
                    new TenantAccessDeniedException(), req);

            // 404 — never reveals existence (security-blueprint §11)
            assertThat(response.getStatusCode().value()).isEqualTo(404);

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(audit, times(1)).record(captor.capture());
            AuditEvent recorded = captor.getValue();

            assertThat(recorded.action())
                    .as("must use the canonical action constant")
                    .isEqualTo(AuditAction.CROSS_TENANT_OR_PROJECT_ACCESS_ATTEMPT);
            assertThat(recorded.actorUserId()).isEqualTo(actorUserId);
            assertThat(recorded.tenantId()).isEqualTo(tenantId);
            assertThat(recorded.reason())
                    .as("redacted detail = method + path, no body/query")
                    .isEqualTo("GET /api/v1/positions/00000000-0000-0000-0000-000000000999");
            assertThat(recorded.ipAddress()).isEqualTo("10.0.0.5");
            assertThat(recorded.userAgent()).isEqualTo("Mozilla/5.0 (test)");
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * F-205 fail-closed: a JWT (or DevAuthentication) presents an
     * {@code active_tenant_id} for a tenant the user is NOT a member of
     * in {@code user_tenant_memberships}. {@link TenantContextFilter} must:
     * <ol>
     *   <li>short-circuit with HTTP 403 BEFORE any controller runs;</li>
     *   <li>emit a {@link AuditAction#TENANT_MEMBERSHIP_MISMATCH} audit row;</li>
     *   <li>never invoke the downstream {@link FilterChain}.</li>
     * </ol>
     */
    @Test
    void forgedJwtWithUnassignedTenantIsRejected() throws Exception {
        AuditService audit = mock(AuditService.class);
        UserTenantMembershipRepository memberships = mock(UserTenantMembershipRepository.class);
        JwtTenantContextResolver jwtResolver = mock(JwtTenantContextResolver.class);

        UUID userId      = UUID.randomUUID();
        UUID acmeTenant  = UUID.randomUUID(); // user IS a member of this one
        UUID betaTenant  = UUID.randomUUID(); // ...but NOT this one

        // user_tenant_memberships returns ONLY acmeTenant for this user
        UserTenantMembershipJpaEntity acmeRow = mock(UserTenantMembershipJpaEntity.class);
        when(acmeRow.getTenantId()).thenReturn(acmeTenant);
        when(memberships.findAllByUserId(userId)).thenReturn(List.of(acmeRow));

        // Forged context: active_tenant_id = betaTenant (NOT in memberships)
        TenantContext forgedCtx = new TenantContext(
                userId, betaTenant, Set.of(), Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("POSITION_READ"), Set.of(), false, "ru-RU");
        DevAuthentication forgedAuth = new DevAuthentication(userId.toString(), forgedCtx);
        SecurityContextHolder.getContext().setAuthentication(forgedAuth);

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        TenantContextFilter filter = new TenantContextFilter(
                jwtResolver, memberships, audit, objectMapper, mock(JdbcTemplate.class));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/positions");
        request.addHeader("User-Agent", "Mozilla/5.0 (test)");
        request.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        try {
            filter.doFilter(request, response, chain);

            // 1) chain NEVER invoked — request is short-circuited
            verify(chain, never()).doFilter(request, response);
            // 2) HTTP 403 with canonical PERMISSION_DENIED envelope
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("PERMISSION_DENIED");
            // 3) Tamper-evident audit row with the new canonical action constant
            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(audit, times(1)).record(captor.capture());
            AuditEvent recorded = captor.getValue();
            assertThat(recorded.action()).isEqualTo(AuditAction.TENANT_MEMBERSHIP_MISMATCH);
            assertThat(recorded.actorUserId()).isEqualTo(userId);
            assertThat(recorded.tenantId())
                    .as("attempted (forged) tenant id is captured, not the legitimate one")
                    .isEqualTo(betaTenant);
            assertThat(recorded.reason()).isEqualTo("GET /api/v1/positions");
            assertThat(recorded.ipAddress()).isEqualTo("10.0.0.5");
            assertThat(recorded.userAgent()).isEqualTo("Mozilla/5.0 (test)");
        } finally {
            SecurityContextHolder.clearContext();
            TenantContextHolder.clear();
        }
    }

    /**
     * TI-12 (manipulated tenant_id in request BODY). A POST /api/v1/projects
     * carries a forged {@code "tenant_id": "<Beta-UUID>"} JSON property while
     * the JWT-derived {@link TenantContext} is bound to ACME. The contract
     * (security-blueprint §20.1 finding 2): the backend must derive the
     * tenant from the authenticated context, NEVER from the request body.
     *
     * <p>Proof at the filter layer: after {@link TenantContextFilter} runs:
     * <ol>
     *   <li>the downstream chain IS invoked (membership check passes — user is
     *       a legitimate ACME member);</li>
     *   <li>{@link TenantContextHolder#get()} returns the ACME tenant id;</li>
     *   <li>no body parsing happens in the filter; the {@code tenant_id}
     *       JSON property never influences the per-request tenant.</li>
     * </ol>
     */
    @Test
    void tenantIdInRequestBodyIsIgnored() throws Exception {
        AuditService audit = mock(AuditService.class);
        UserTenantMembershipRepository memberships = mock(UserTenantMembershipRepository.class);
        JwtTenantContextResolver jwtResolver = mock(JwtTenantContextResolver.class);

        UUID userId     = UUID.randomUUID();
        UUID acmeTenant = UUID.randomUUID();
        UUID betaTenant = UUID.randomUUID(); // forged value in body

        UserTenantMembershipJpaEntity acmeRow = mock(UserTenantMembershipJpaEntity.class);
        when(acmeRow.getTenantId()).thenReturn(acmeTenant);
        when(memberships.findAllByUserId(userId)).thenReturn(List.of(acmeRow));

        TenantContext acmeCtx = new TenantContext(
                userId, acmeTenant, Set.of(), Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("PROJECT_CREATE"), Set.of(), false, "ru-RU");
        SecurityContextHolder.getContext().setAuthentication(
                new DevAuthentication(userId.toString(), acmeCtx));

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        TenantContextFilter filter = new TenantContextFilter(
                jwtResolver, memberships, audit, objectMapper, mock(JdbcTemplate.class));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/projects");
        request.setContentType("application/json");
        // Forged body — tenant_id points at Beta.
        String forgedBody = "{\"code\":\"PRJ-FORGE\",\"name_i18n\":{\"ru-RU\":\"X\"},"
                + "\"tenant_id\":\"" + betaTenant + "\"}";
        request.setContent(forgedBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        request.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UUID[] observedTenant = new UUID[1];
        FilterChain chain = (req, resp) -> {
            TenantContext active = TenantContextHolder.get();
            observedTenant[0] = active == null ? null : active.tenantId();
        };

        try {
            filter.doFilter(request, response, chain);

            // 1) chain WAS invoked — user is a legitimate ACME member.
            assertThat(observedTenant[0])
                    .as("filter binds tenantId from authenticated context, NOT from body")
                    .isEqualTo(acmeTenant)
                    .isNotEqualTo(betaTenant);
            // 2) no membership-mismatch audit row (request was legitimate)
            verify(audit, never()).record(org.mockito.ArgumentMatchers.any());
        } finally {
            SecurityContextHolder.clearContext();
            TenantContextHolder.clear();
        }
    }

    /**
     * TI-13 (manipulated tenant_id in QUERY string). GET /api/v1/projects
     * with {@code ?tenantId=<Beta-UUID>} while the JWT-derived context is
     * ACME. {@link TenantContextFilter} must ignore the query parameter and
     * bind the ACME tenant.
     */
    @Test
    void tenantIdInQueryStringIsIgnored() throws Exception {
        AuditService audit = mock(AuditService.class);
        UserTenantMembershipRepository memberships = mock(UserTenantMembershipRepository.class);
        JwtTenantContextResolver jwtResolver = mock(JwtTenantContextResolver.class);

        UUID userId     = UUID.randomUUID();
        UUID acmeTenant = UUID.randomUUID();
        UUID betaTenant = UUID.randomUUID();

        UserTenantMembershipJpaEntity acmeRow = mock(UserTenantMembershipJpaEntity.class);
        when(acmeRow.getTenantId()).thenReturn(acmeTenant);
        when(memberships.findAllByUserId(userId)).thenReturn(List.of(acmeRow));

        TenantContext acmeCtx = new TenantContext(
                userId, acmeTenant, Set.of(), Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("PROJECT_READ"), Set.of(), false, "ru-RU");
        SecurityContextHolder.getContext().setAuthentication(
                new DevAuthentication(userId.toString(), acmeCtx));

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        TenantContextFilter filter = new TenantContextFilter(
                jwtResolver, memberships, audit, objectMapper, mock(JdbcTemplate.class));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
        request.setParameter("tenantId", betaTenant.toString());     // forged
        request.setParameter("tenant_id", betaTenant.toString());    // belt-and-braces
        request.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UUID[] observedTenant = new UUID[1];
        FilterChain chain = (req, resp) -> {
            TenantContext active = TenantContextHolder.get();
            observedTenant[0] = active == null ? null : active.tenantId();
        };

        try {
            filter.doFilter(request, response, chain);
            assertThat(observedTenant[0])
                    .as("filter binds tenantId from authenticated context, NOT from query")
                    .isEqualTo(acmeTenant)
                    .isNotEqualTo(betaTenant);
            verify(audit, never()).record(org.mockito.ArgumentMatchers.any());
        } finally {
            SecurityContextHolder.clearContext();
            TenantContextHolder.clear();
        }
    }

    /**
     * TI-14 (custom {@code X-Tenant-Id} HEADER). Same threat shape as TI-12/13
     * — caller tries to override the authenticated tenant via a custom HTTP
     * header. {@link TenantContextFilter} must ignore unknown tenant headers.
     */
    @Test
    void tenantIdInCustomHeaderIsIgnored() throws Exception {
        AuditService audit = mock(AuditService.class);
        UserTenantMembershipRepository memberships = mock(UserTenantMembershipRepository.class);
        JwtTenantContextResolver jwtResolver = mock(JwtTenantContextResolver.class);

        UUID userId     = UUID.randomUUID();
        UUID acmeTenant = UUID.randomUUID();
        UUID betaTenant = UUID.randomUUID();

        UserTenantMembershipJpaEntity acmeRow = mock(UserTenantMembershipJpaEntity.class);
        when(acmeRow.getTenantId()).thenReturn(acmeTenant);
        when(memberships.findAllByUserId(userId)).thenReturn(List.of(acmeRow));

        TenantContext acmeCtx = new TenantContext(
                userId, acmeTenant, Set.of(), Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("PROJECT_READ"), Set.of(), false, "ru-RU");
        SecurityContextHolder.getContext().setAuthentication(
                new DevAuthentication(userId.toString(), acmeCtx));

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        TenantContextFilter filter = new TenantContextFilter(
                jwtResolver, memberships, audit, objectMapper, mock(JdbcTemplate.class));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
        request.addHeader("X-Tenant-Id", betaTenant.toString());     // forged
        request.addHeader("X-Tenant-Override", betaTenant.toString());
        request.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UUID[] observedTenant = new UUID[1];
        FilterChain chain = (req, resp) -> {
            TenantContext active = TenantContextHolder.get();
            observedTenant[0] = active == null ? null : active.tenantId();
        };

        try {
            filter.doFilter(request, response, chain);
            assertThat(observedTenant[0])
                    .as("filter binds tenantId from authenticated context, NOT from custom headers")
                    .isEqualTo(acmeTenant)
                    .isNotEqualTo(betaTenant);
            verify(audit, never()).record(org.mockito.ArgumentMatchers.any());
        } finally {
            SecurityContextHolder.clearContext();
            TenantContextHolder.clear();
        }
    }

    @Test
    void auditFailureDoesNotMaskThe404Response() {
        AuditService brokenAudit = mock(AuditService.class);
        org.mockito.Mockito.doThrow(new RuntimeException("audit DB down"))
                .when(brokenAudit).record(org.mockito.ArgumentMatchers.any());

        GlobalExceptionHandler handler = new GlobalExceptionHandler(brokenAudit);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/v1/positions/x");
        when(req.getMethod()).thenReturn("GET");
        when(req.getRemoteAddr()).thenReturn("10.0.0.5");

        var response = handler.handleTenantAccessDenied(new TenantAccessDeniedException(), req);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }
}
