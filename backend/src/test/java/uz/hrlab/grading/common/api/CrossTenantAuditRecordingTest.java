package uz.hrlab.grading.common.api;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
