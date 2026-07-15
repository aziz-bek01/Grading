package uz.hrlab.grading.access.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.access.api.AuditIntegrityResponse;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditChainVerificationResult;
import uz.hrlab.grading.audit.application.AuditChainVerifier;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditJsonRedactor;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MVP1-E10-1 — orchestration contract for {@code GET /api/v1/audit/integrity}:
 * AUDIT_READ enforcement (defence in depth), tenant sourced from context (never
 * input), the AUDIT_INTEGRITY_VERIFIED forensic emit, and DTO mapping.
 */
@Tag("audit")
class VerifyAuditIntegrityQueryTest {

    private final AuditChainVerifier verifier = mock(AuditChainVerifier.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AuditJsonRedactor redactor = new AuditJsonRedactor(new ObjectMapper());
    private final VerifyAuditIntegrityQuery query =
            new VerifyAuditIntegrityQuery(verifier, auditService, redactor);

    private final UUID tenant = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    private void setContext(Set<String> permissions) {
        TenantContextHolder.set(new TenantContext(
                user, tenant, Set.of(), Set.of("EXTERNAL_AUDITOR"),
                permissions, Set.of(), false, "ru-RU"));
    }

    private AuditChainVerificationResult intactResult() {
        return new AuditChainVerificationResult(
                tenant, AuditChainVerificationResult.Status.INTACT,
                10, 10, 8, 2, OffsetDateTime.parse("2026-07-15T00:00:00Z"),
                OffsetDateTime.now(ZoneOffset.UTC), null, null,
                false, AuditChainVerifier.DEFAULT_MAX_ROWS, null,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void deniesWithoutAuditReadPermission() {
        setContext(Set.of("AUDIT_VIEW")); // reader alias is NOT enough

        assertThatThrownBy(() -> query.verify(null, null, null))
                .isInstanceOf(PermissionDeniedException.class);
        verify(verifier, never()).verify(any(), any(), any(), any());
        verify(auditService, never()).record(any());
    }

    @Test
    void verifiesTheContextTenantOnly() {
        setContext(Set.of("AUDIT_READ"));
        when(verifier.verify(eq(tenant), any(), any(), any())).thenReturn(intactResult());

        OffsetDateTime from = OffsetDateTime.parse("2026-07-01T00:00:00Z");
        query.verify(from, null, 100);

        // The tenant handed to the verifier is the context tenant — never input.
        verify(verifier).verify(eq(tenant), eq(from), eq(null), eq(100));
    }

    @Test
    void emitsAuditIntegrityVerifiedForensicRow() {
        setContext(Set.of("AUDIT_READ"));
        when(verifier.verify(any(), any(), any(), any())).thenReturn(intactResult());

        query.verify(null, null, null);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(1)).record(captor.capture());
        AuditEvent ev = captor.getValue();
        assertThat(ev.action()).isEqualTo(AuditAction.AUDIT_INTEGRITY_VERIFIED);
        assertThat(ev.tenantId()).isEqualTo(tenant);
        assertThat(ev.actorUserId()).isEqualTo(user);
        assertThat(ev.entityType()).isEqualTo("AuditChain");
        assertThat(ev.reason()).contains("status=INTACT").contains("checked=10")
                .contains("verified=8").contains("legacy=2");
    }

    @Test
    void mapsResultToSnakeCaseDto() {
        setContext(Set.of("AUDIT_READ"));
        UUID rowId = UUID.randomUUID();
        var broken = new AuditChainVerificationResult(
                tenant, AuditChainVerificationResult.Status.BROKEN,
                3, 9, 3, 0, OffsetDateTime.parse("2026-07-14T18:00:00Z"),
                OffsetDateTime.parse("2026-07-14T18:02:00Z"), null, null,
                false, 50000,
                new AuditChainVerificationResult.Break(rowId,
                        OffsetDateTime.parse("2026-07-14T18:03:00Z"),
                        AuditChainVerificationResult.BreakType.HASH_MISMATCH,
                        "expectedHash", "actualHash"),
                OffsetDateTime.parse("2026-07-15T09:00:00Z"));
        when(verifier.verify(any(), any(), any(), any())).thenReturn(broken);

        AuditIntegrityResponse dto = query.verify(null, null, null);

        assertThat(dto.status()).isEqualTo("BROKEN");
        assertThat(dto.intact()).isFalse();
        assertThat(dto.checkedCount()).isEqualTo(3);
        assertThat(dto.chainLength()).isEqualTo(9);
        assertThat(dto.independentlyVerifiedCount()).isEqualTo(3);
        assertThat(dto.legacyUnverifiableCount()).isZero();
        assertThat(dto.verifiableFrom()).isEqualTo(OffsetDateTime.parse("2026-07-14T18:00:00Z"));
        assertThat(dto.firstBreak()).isNotNull();
        assertThat(dto.firstBreak().rowId()).isEqualTo(rowId);
        assertThat(dto.firstBreak().breakType()).isEqualTo("HASH_MISMATCH");
        assertThat(dto.firstBreak().expectedHash()).isEqualTo("expectedHash");
        assertThat(dto.firstBreak().actualHash()).isEqualTo("actualHash");
    }

    @Test
    void auditEmitFailureDoesNotFailVerification() {
        setContext(Set.of("AUDIT_READ"));
        when(verifier.verify(any(), any(), any(), any())).thenReturn(intactResult());
        doThrow(new RuntimeException("audit DB down")).when(auditService).record(any());

        AuditIntegrityResponse dto = query.verify(null, null, null);

        assertThat(dto.status()).isEqualTo("INTACT"); // read succeeded regardless
    }
}
