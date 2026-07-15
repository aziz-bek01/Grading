package uz.hrlab.grading.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.access.api.AuditIntegrityResponse;
import uz.hrlab.grading.access.application.VerifyAuditIntegrityQuery;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogJpaEntity;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MVP1-E10-1 — end-to-end proof that the integrity verifier recomputes hashes
 * IDENTICALLY to the writer across a real PostgreSQL round-trip (the whole point:
 * a verifier that hashes differently than the writer is useless). Docker-gated
 * via {@link AbstractIntegrationTest} / {@code RequiresDocker}.
 */
@Tag("audit")
@Tag("integration")
class AuditChainIntegrityIntegrationTest extends AbstractIntegrationTest {

    @Autowired AuditService auditService;
    @Autowired SystemAuditLogRepository repository;
    @Autowired VerifyAuditIntegrityQuery verifyAuditIntegrity;

    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    private void writeEvent(UUID tenantId, String action, UUID entityId) {
        auditService.record(AuditEvent.builder()
                .tenantId(tenantId)
                .actorUserId(UUID.randomUUID())
                .action(action)
                .entityType("Tenant")
                .entityId(entityId)
                .reason("integrity-test")
                .build());
    }

    /**
     * M2 — write a payload carrying numbers that diverge across a jsonb round-trip
     * (scientific, trailing-zero decimal, 20-digit int) and prove the v2 content
     * re-hash is still deterministic (INTACT) after the real DB round-trip.
     */
    @Test
    void numericPayloadRoundTripsAndVerifiesIntact() throws Exception {
        UUID tenant = UUID.randomUUID();
        var after = mapper.readTree(
                "{\"sci\":1.0E10,\"dec\":1234.50,\"big\":12345678901234567890}");
        auditService.record(AuditEvent.builder()
                .tenantId(tenant)
                .actorUserId(UUID.randomUUID())
                .action(AuditAction.METHODOLOGY_UPDATED)
                .entityType("Methodology")
                .entityId(UUID.randomUUID())
                .afterJson(after)
                .build());

        asAuditor(tenant, () -> {
            AuditIntegrityResponse result = verifyAuditIntegrity.verify(null, null, null);
            assertThat(result.status()).isEqualTo("INTACT");
            assertThat(result.independentlyVerifiedCount()).isEqualTo(1);
            assertThat(result.firstBreak()).isNull();
        });
    }

    private void asAuditor(UUID tenantId, Runnable body) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(),
                Set.of("EXTERNAL_AUDITOR"), Set.of("AUDIT_READ"),
                Set.of(), false, "ru-RU"));
        try {
            body.run();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void realWrittenChainVerifiesIntact() {
        UUID tenant = UUID.randomUUID();
        writeEvent(tenant, AuditAction.TENANT_CREATED, tenant);
        writeEvent(tenant, AuditAction.PROJECT_CREATED, UUID.randomUUID());
        writeEvent(tenant, AuditAction.POSITION_CREATED, UUID.randomUUID());

        asAuditor(tenant, () -> {
            AuditIntegrityResponse result = verifyAuditIntegrity.verify(null, null, null);
            assertThat(result.status()).isEqualTo("INTACT");
            assertThat(result.intact()).isTrue();
            assertThat(result.checkedCount()).isEqualTo(3);
            assertThat(result.chainLength()).isEqualTo(3);
            // All rows are writer-produced current-format (v2) → independently verified.
            assertThat(result.independentlyVerifiedCount()).isEqualTo(3);
            assertThat(result.legacyUnverifiableCount()).isZero();
            assertThat(result.verifiableFrom()).isNotNull();
            assertThat(result.firstBreak()).isNull();
            assertThat(result.verifiedThrough()).isNotNull();
        });
    }

    @Test
    void legacyFormatRowIsLinkageIntactButNotContentVerified() {
        UUID tenant = UUID.randomUUID();
        writeEvent(tenant, AuditAction.TENANT_CREATED, tenant);
        writeEvent(tenant, AuditAction.PROJECT_CREATED, UUID.randomUUID());

        // Simulate a pre-hardening (v1) row appended to the chain: valid linkage,
        // arbitrary stored hash_current, hash_format_version = 1. The verifier
        // must NOT recompute it and must NOT flag it — chain stays INTACT.
        SystemAuditLogJpaEntity newest =
                repository.findFirstByTenantIdOrderByCreatedAtDesc(tenant).orElseThrow();
        UUID legacyId = UUID.randomUUID();
        OffsetDateTime legacyTs = newest.getCreatedAt().plusSeconds(1);
        jdbcTemplate.update(
                "INSERT INTO public.system_audit_log "
                        + "(id, tenant_id, action, entity_type, entity_id, created_at, hash_prev, hash_current, hash_format_version) "
                        + "VALUES (?, ?, 'POSITION_CREATED', 'Tenant', ?, ?, ?, 'legacy-format-hash', 1)",
                legacyId, tenant, UUID.randomUUID(), legacyTs, newest.getHashCurrent());

        asAuditor(tenant, () -> {
            AuditIntegrityResponse result = verifyAuditIntegrity.verify(null, null, null);
            assertThat(result.status()).isEqualTo("INTACT");
            assertThat(result.checkedCount()).isEqualTo(3);
            assertThat(result.independentlyVerifiedCount()).isEqualTo(2); // the two v2 rows
            assertThat(result.legacyUnverifiableCount()).isEqualTo(1);    // the simulated v1 row
            assertThat(result.firstBreak()).isNull();
        });
    }

    @Test
    void tamperedRowIsDetectedWithFirstOffenderAndBreakType() {
        UUID tenant = UUID.randomUUID();
        writeEvent(tenant, AuditAction.TENANT_CREATED, tenant);
        writeEvent(tenant, AuditAction.PROJECT_CREATED, UUID.randomUUID());

        // The newest real row's hash_current is a valid predecessor link.
        SystemAuditLogJpaEntity newest =
                repository.findFirstByTenantIdOrderByCreatedAtDesc(tenant).orElseThrow();

        // Splice a CURRENT-format (v2) row into the chain: valid prev-link, but a
        // FORGED hash_current that no honest recomputation can reproduce. Because
        // it is stamped v2, the verifier recomputes it → HASH_MISMATCH.
        UUID forgedId = UUID.randomUUID();
        OffsetDateTime forgedTs = newest.getCreatedAt().plusSeconds(1);
        jdbcTemplate.update(
                "INSERT INTO public.system_audit_log "
                        + "(id, tenant_id, action, entity_type, entity_id, created_at, hash_prev, hash_current, hash_format_version) "
                        + "VALUES (?, ?, 'POSITION_UPDATED', 'Tenant', ?, ?, ?, 'forged-not-a-real-sha256', 2)",
                forgedId, tenant, UUID.randomUUID(), forgedTs, newest.getHashCurrent());

        asAuditor(tenant, () -> {
            AuditIntegrityResponse result = verifyAuditIntegrity.verify(null, null, null);
            assertThat(result.status()).isEqualTo("BROKEN");
            assertThat(result.intact()).isFalse();
            assertThat(result.firstBreak()).isNotNull();
            assertThat(result.firstBreak().rowId()).isEqualTo(forgedId);
            assertThat(result.firstBreak().breakType()).isEqualTo("HASH_MISMATCH");
            assertThat(result.firstBreak().actualHash()).isEqualTo("forged-not-a-real-sha256");
            assertThat(result.firstBreak().expectedHash()).isNotEqualTo("forged-not-a-real-sha256");
        });
    }

    @Test
    void verificationNeverSeesAnotherTenantsRows() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        writeEvent(tenantA, AuditAction.TENANT_CREATED, tenantA);
        writeEvent(tenantA, AuditAction.PROJECT_CREATED, UUID.randomUUID());
        // Tenant B has a DIFFERENT number of rows in the same physical table.
        writeEvent(tenantB, AuditAction.TENANT_CREATED, tenantB);
        writeEvent(tenantB, AuditAction.PROJECT_CREATED, UUID.randomUUID());
        writeEvent(tenantB, AuditAction.POSITION_CREATED, UUID.randomUUID());

        asAuditor(tenantA, () -> {
            AuditIntegrityResponse result = verifyAuditIntegrity.verify(null, null, null);
            assertThat(result.tenantId()).isEqualTo(tenantA);
            assertThat(result.status()).isEqualTo("INTACT");
            // Only tenant A's 2 rows — tenant B's 3 are invisible to this chain.
            assertThat(result.checkedCount()).isEqualTo(2);
            assertThat(result.chainLength()).isEqualTo(2);
        });

        asAuditor(tenantB, () -> {
            AuditIntegrityResponse result = verifyAuditIntegrity.verify(null, null, null);
            assertThat(result.tenantId()).isEqualTo(tenantB);
            assertThat(result.status()).isEqualTo("INTACT");
            assertThat(result.checkedCount()).isEqualTo(3);
        });
    }

    @Test
    void windowedVerificationChecksOnlyTheSlice() {
        UUID tenant = UUID.randomUUID();
        writeEvent(tenant, AuditAction.TENANT_CREATED, tenant);
        OffsetDateTime cut = OffsetDateTime.now(ZoneOffset.UTC);
        writeEvent(tenant, AuditAction.PROJECT_CREATED, UUID.randomUUID());
        writeEvent(tenant, AuditAction.POSITION_CREATED, UUID.randomUUID());

        asAuditor(tenant, () -> {
            AuditIntegrityResponse result = verifyAuditIntegrity.verify(cut, null, null);
            // Only rows at/after the cut are checked; the pre-cut genesis is
            // excluded, and the window's first row's inbound link is accepted.
            assertThat(result.status()).isEqualTo("INTACT");
            assertThat(result.checkedCount()).isEqualTo(2);
            assertThat(result.from()).isNotNull();
        });
    }
}
