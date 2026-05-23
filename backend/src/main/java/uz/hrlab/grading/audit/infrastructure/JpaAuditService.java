package uz.hrlab.grading.audit.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Default {@link AuditService} — writes one row per event to
 * {@code public.system_audit_log} with SHA-256 hash chaining
 * (security-blueprint §9.2).
 *
 * <p>Always runs in {@link Propagation#REQUIRES_NEW} so an audit insert is
 * never rolled back by a caller's transaction failure (architecture §8.5).
 */
@Service
public class JpaAuditService implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(JpaAuditService.class);

    private final SystemAuditLogRepository repository;
    private final ObjectMapper objectMapper;

    @Autowired
    public JpaAuditService(SystemAuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        // Order keys deterministically so the hash is reproducible.
        this.objectMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        UUID id = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();
        String beforeJson = serialize(event.beforeJson());
        String afterJson = serialize(event.afterJson());
        String prevHash = repository.findLastHash(event.tenantId()).orElse(null);
        String currentHash = computeHash(id, event, beforeJson, afterJson, createdAt, prevHash);

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
                currentHash
        );
        repository.save(row);
    }

    private String serialize(JsonNode node) {
        if (node == null) return null;
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            log.warn("Audit JSON serialize failed: {}", ex.getMessage());
            return null;
        }
    }

    private String computeHash(UUID id, AuditEvent event, String beforeJson, String afterJson,
                               OffsetDateTime createdAt, String prevHash) {
        StringBuilder sb = new StringBuilder();
        sb.append(id);
        sb.append('|').append(safe(event.tenantId()));
        sb.append('|').append(safe(event.projectId()));
        sb.append('|').append(safe(event.actorUserId()));
        sb.append('|').append(event.action() == null ? "" : event.action());
        sb.append('|').append(event.entityType() == null ? "" : event.entityType());
        sb.append('|').append(safe(event.entityId()));
        sb.append('|').append(beforeJson == null ? "" : beforeJson);
        sb.append('|').append(afterJson == null ? "" : afterJson);
        sb.append('|').append(createdAt);
        sb.append('|').append(prevHash == null ? "" : prevHash);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandatory in every JDK; if missing fail closed.
            throw new IllegalStateException("SHA-256 missing", ex);
        }
    }

    private String safe(Object obj) {
        return obj == null ? "" : obj.toString();
    }
}
