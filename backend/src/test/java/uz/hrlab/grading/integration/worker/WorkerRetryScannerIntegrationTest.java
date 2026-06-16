package uz.hrlab.grading.integration.worker;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hrlab.grading.AbstractIntegrationTest;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Batch-4: proves the re-queuer's candidate scan only picks up rows that are
 * (a) in the retryable FAILED state, (b) DUE ({@code next_attempt_at <= now} or
 * null), (c) UNDER the retry bound, and (d) belong to the scanned tenant.
 * DEAD_LETTER and other-tenant rows are never selected.
 *
 * <p>Testcontainers-gated (skips offline). The default container user is a
 * superuser so RLS is bypassed — this test therefore asserts the finder's
 * explicit {@code tenant_id} predicate (defence-in-depth), which is what must
 * hold even when RLS is not the gate.
 */
@Tag("integration")
@Tag("tenant-isolation")
class WorkerRetryScannerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    WorkerRetryScanner scanner;

    @Test
    void scanSelectsOnlyDueUnderMaxRetryableRowsForTheTenant() {
        UUID tenantA = newSeededTenantId();
        UUID tenantB = newSeededTenantId();
        UUID projectA = insertProject(tenantA, "PRJ-A");
        UUID projectB = insertProject(tenantB, "PRJ-B");
        OffsetDateTime now = OffsetDateTime.now();

        // (1) DUE retryable FAILED, attempt 1 — picked up.
        UUID dueNullNext = insertExportJob(tenantA, projectA, "FAILED", 1, null);
        UUID duePastNext = insertExportJob(tenantA, projectA, "FAILED", 2, now.minusMinutes(5));
        // (2) NOT due yet (future next_attempt_at) — skipped.
        UUID notDue = insertExportJob(tenantA, projectA, "FAILED", 1, now.plusMinutes(30));
        // (3) At the bound — skipped (re-queuer must not loop forever).
        insertExportJob(tenantA, projectA, "FAILED", WorkerRetryPolicy.MAX_ATTEMPTS, null);
        // (4) Terminal DEAD_LETTER — never selected.
        insertExportJob(tenantA, projectA, "DEAD_LETTER", WorkerRetryPolicy.MAX_ATTEMPTS, null);
        // (5) Non-failed status — not a retry candidate.
        insertExportJob(tenantA, projectA, "GENERATED", 0, null);
        // (6) Other tenant, due + retryable — must NOT leak into tenant A's scan.
        insertExportJob(tenantB, projectB, "FAILED", 1, null);

        TenantContext previous = TenantContextHolder.get();
        TenantContextHolder.set(new TenantContext(
                null, tenantA, Set.of(), Set.of(), Set.of(), Set.of(), false, null));
        try {
            List<UUID> due = scanner.dueExports(tenantA, now).stream()
                    .map(WorkerReQueuer.DueRow::id).collect(Collectors.toList());

            assertThat(due).containsExactlyInAnyOrder(dueNullNext, duePastNext);
            assertThat(due).doesNotContain(notDue);
        } finally {
            if (previous == null) TenantContextHolder.clear();
            else TenantContextHolder.set(previous);
        }
    }

    private UUID insertProject(UUID tenantId, String code) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.projects (id, tenant_id, code, name_i18n, status, version) "
                        + "VALUES (?, ?, ?, '{\"ru-RU\":\"X\"}'::jsonb, 'DRAFT', 0)",
                id, tenantId, code);
        return id;
    }

    private UUID insertExportJob(UUID tenantId, UUID projectId, String status,
                                 int attemptCount, OffsetDateTime nextAttemptAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.export_jobs (id, tenant_id, project_id, export_type, format, "
                        + "status, requested_by, requested_at, contains_salary_data, "
                        + "contains_personal_data, attempt_count, next_attempt_at, version) "
                        + "VALUES (?, ?, ?, 'POSITION_CATALOG', 'XLSX', ?, ?, now(), false, false, ?, ?, 0)",
                id, tenantId, projectId, status, UUID.randomUUID(), attemptCount, nextAttemptAt);
        return id;
    }
}
