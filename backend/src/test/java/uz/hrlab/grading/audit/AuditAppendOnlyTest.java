package uz.hrlab.grading.audit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogRepository;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks down the append-only contract on the audit log
 * (architecture §8.5, security-blueprint §9.1, §20.1 audit hash chaining).
 *
 * <p>If anyone ever adds {@code delete(...)} or {@code save} that performs an
 * UPDATE to {@code SystemAuditLogRepository} this test must fail loudly.
 */
@Tag("audit")
@Tag("integration")
class AuditAppendOnlyTest extends AbstractIntegrationTest {

    @Autowired AuditService auditService;
    @Autowired SystemAuditLogRepository repository;

    @Test
    void repositoryExposesNoDeleteMethods() {
        Method[] methods = SystemAuditLogRepository.class.getMethods();
        assertThat(Arrays.stream(methods).map(Method::getName))
                .as("audit repository must not expose any delete operations")
                .noneMatch(n -> n.startsWith("delete") || n.equals("remove") || n.equals("removeAll"));
    }

    @Test
    void repositoryExposesNoBulkUpdateMethods() {
        Method[] methods = SystemAuditLogRepository.class.getMethods();
        assertThat(Arrays.stream(methods).map(Method::getName))
                .as("audit repository must not expose update / saveAll mass operations")
                .noneMatch(n -> n.equals("saveAll") || n.equals("saveAllAndFlush") || n.startsWith("update"));
    }

    @Test
    void hashChainIsComputedAndChainedAcrossEvents() {
        UUID tenantId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        auditService.record(AuditEvent.builder()
                .tenantId(tenantId).actorUserId(actor)
                .action(AuditAction.TENANT_CREATED)
                .entityType("Tenant").entityId(tenantId)
                .build());

        auditService.record(AuditEvent.builder()
                .tenantId(tenantId).actorUserId(actor)
                .action(AuditAction.PROJECT_CREATED)
                .entityType("Project").entityId(UUID.randomUUID())
                .build());

        var rows = repository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        assertThat(rows).hasSize(2);

        // Second event's hash_prev must equal the first event's hash_current.
        var newest = rows.get(0);
        var oldest = rows.get(1);
        assertThat(newest.getHashPrev()).isEqualTo(oldest.getHashCurrent());
        assertThat(newest.getHashCurrent()).isNotBlank();
        assertThat(oldest.getHashPrev()).isNull();
    }
}
