package uz.hrlab.grading.audit.infrastructure;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditHashCalculator;
import uz.hrlab.grading.audit.application.AuditHashInput;
import uz.hrlab.grading.audit.application.AuditService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Default {@link AuditService} — writes one row per event to
 * {@code public.system_audit_log} with SHA-256 hash chaining
 * (security-blueprint §9.2).
 *
 * <p>Always runs in {@link Propagation#REQUIRES_NEW} so an audit insert is
 * never rolled back by a caller's transaction failure (architecture §8.5).
 *
 * <p>The hash itself is computed by the shared {@link AuditHashCalculator} —
 * the SAME component the read-side {@code AuditChainVerifier} uses — so the
 * writer and the integrity verifier can never diverge.
 */
@Service
public class JpaAuditService implements AuditService {

    private final SystemAuditLogRepository repository;
    private final AuditHashCalculator hashCalculator;

    @Autowired
    public JpaAuditService(SystemAuditLogRepository repository, AuditHashCalculator hashCalculator) {
        this.repository = repository;
        this.hashCalculator = hashCalculator;
    }

    /**
     * Sentinel advisory-lock key for the null-tenant (control-plane / platform)
     * audit chain, so all platform-scoped appends serialize on one slot just as
     * each tenant's appends do.
     */
    private static final String CONTROL_PLANE_LOCK_KEY = "__control_plane_audit_chain__";

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        // P2-FIX (Task 4) — serialize hash-chain appends within this tenant
        // chain via a transaction-scoped advisory lock taken BEFORE the
        // read-then-write so two concurrent audited actions cannot read the same
        // prevHash and fork the chain. Auto-releases on commit/rollback.
        repository.acquireTenantAuditChainLock(lockKey(event.tenantId()));

        UUID id = UUID.randomUUID();
        // UTC + microsecond precision so the value STORED in TIMESTAMPTZ and the
        // value HASHED describe the identical instant — the audit chain verifier
        // recomputes from the stored value and must land on the same hash.
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        String beforeJson = hashCalculator.canonicalJson(event.beforeJson());
        String afterJson = hashCalculator.canonicalJson(event.afterJson());
        String prevHash = repository.findLastHash(event.tenantId()).orElse(null);
        String currentHash = hashCalculator.compute(new AuditHashInput(
                id,
                event.tenantId(),
                event.projectId(),
                event.actorUserId(),
                event.action(),
                event.entityType(),
                event.entityId(),
                beforeJson,
                afterJson,
                createdAt,
                AuditHashCalculator.HASH_FORMAT_VERSION,
                prevHash));

        SystemAuditLogJpaEntity row = new SystemAuditLogJpaEntity(
                id,
                event.tenantId(),
                event.projectId(),
                event.actorUserId(),
                event.action(),
                event.entityType(),
                event.entityId(),
                beforeJson,
                afterJson,
                event.reason(),
                event.ipAddress(),
                event.userAgent(),
                event.correlationId(),
                event.traceId(),
                createdAt,
                prevHash,
                currentHash,
                AuditHashCalculator.HASH_FORMAT_VERSION
        );
        repository.save(row);
    }

    /** Advisory-lock key for a tenant chain — null tenant maps to a fixed sentinel. */
    private String lockKey(UUID tenantId) {
        return tenantId == null ? CONTROL_PLANE_LOCK_KEY : tenantId.toString();
    }
}
