package uz.hrlab.grading.audit.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2-FIX (Task 4) — {@link JpaAuditService} per-tenant advisory-lock guard.
 *
 * <p>The hash-chain append is read-then-write ({@code findLastHash} → compute →
 * {@code save}). These tests assert the transaction-scoped per-tenant advisory
 * lock is acquired BEFORE that read, so concurrent appends in the same tenant
 * serialize instead of both chaining off the same {@code prevHash}. Concurrency
 * itself is exercised by the integration suite ({@code AuditAppendOnlyTest});
 * here we pin the lock call + its ordering + its key with mocks (no DB).
 */
@Tag("audit")
@ExtendWith(MockitoExtension.class)
class JpaAuditServiceLockTest {

    @Mock SystemAuditLogRepository repository;

    JpaAuditService service;

    @BeforeEach
    void setUp() {
        service = new JpaAuditService(repository, new ObjectMapper());
    }

    @Test
    void acquiresTenantAdvisoryLockBeforeReadingAndWritingChain() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findLastHash(tenantId)).thenReturn(Optional.empty());

        service.record(AuditEvent.builder()
                .tenantId(tenantId).actorUserId(UUID.randomUUID())
                .action(AuditAction.TENANT_CREATED)
                .entityType("Tenant").entityId(tenantId)
                .build());

        // Lock MUST be taken before the read-then-write.
        InOrder order = inOrder(repository);
        order.verify(repository).acquireTenantAuditChainLock(tenantId.toString());
        order.verify(repository).findLastHash(tenantId);
        order.verify(repository).save(any(SystemAuditLogJpaEntity.class));
    }

    @Test
    void usesTenantIdAsLockKey() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findLastHash(tenantId)).thenReturn(Optional.empty());

        service.record(AuditEvent.builder()
                .tenantId(tenantId).actorUserId(UUID.randomUUID())
                .action(AuditAction.PROJECT_CREATED)
                .entityType("Project").entityId(UUID.randomUUID())
                .build());

        verify(repository).acquireTenantAuditChainLock(eq(tenantId.toString()));
    }

    @Test
    void nullTenantUsesControlPlaneSentinelLockKey() {
        when(repository.findLastHash(null)).thenReturn(Optional.empty());

        service.record(AuditEvent.builder()
                .actorUserId(UUID.randomUUID())
                .action(AuditAction.TENANT_CREATED)
                .entityType("Tenant").entityId(UUID.randomUUID())
                .build());

        // Platform (null-tenant) chain serializes on a single fixed key, never
        // a literal "null" string.
        verify(repository).acquireTenantAuditChainLock(eq("__control_plane_audit_chain__"));
    }
}
