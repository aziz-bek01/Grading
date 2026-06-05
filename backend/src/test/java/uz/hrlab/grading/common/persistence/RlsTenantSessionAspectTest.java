package uz.hrlab.grading.common.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit-level proof that {@link RlsTenantSessionAspect} binds the RLS tenant GUC
 * to the active transaction ONLY when (a) a transaction is actually active and
 * (b) a tenant context with a tenant id is present — and is a safe no-op
 * otherwise. Runs without a database via a {@link MockedStatic} over
 * {@link TransactionSynchronizationManager}.
 */
@Tag("tenant-isolation")
class RlsTenantSessionAspectTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final RlsTenantSessionAspect aspect = new RlsTenantSessionAspect(jdbcTemplate);

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void setsTenantGucWhenTransactionActiveAndContextPresent() {
        UUID tenantId = UUID.randomUUID();
        TenantContextHolder.set(context(tenantId));

        try (MockedStatic<TransactionSynchronizationManager> tx =
                     mockStatic(TransactionSynchronizationManager.class)) {
            tx.when(TransactionSynchronizationManager::isActualTransactionActive).thenReturn(true);

            aspect.bindTenantToTransaction();

            verify(jdbcTemplate, times(1))
                    .execute("SET LOCAL app.tenant_id = '" + tenantId + "'");
        }
    }

    @Test
    void noOpWhenNoTransactionActive() {
        TenantContextHolder.set(context(UUID.randomUUID()));

        try (MockedStatic<TransactionSynchronizationManager> tx =
                     mockStatic(TransactionSynchronizationManager.class)) {
            tx.when(TransactionSynchronizationManager::isActualTransactionActive).thenReturn(false);

            aspect.bindTenantToTransaction();

            // Never set the GUC on a connection that has no transaction —
            // it would be returned to the pool and the setting lost (the
            // original 22P02 root cause).
            verify(jdbcTemplate, never()).execute(anyString());
        }
    }

    @Test
    void noOpWhenNoTenantContext() {
        // Control-plane / admin / unprovisioned request: leave GUC unset.
        // Migration 031 makes that yield zero rows, not 22P02.
        try (MockedStatic<TransactionSynchronizationManager> tx =
                     mockStatic(TransactionSynchronizationManager.class)) {
            tx.when(TransactionSynchronizationManager::isActualTransactionActive).thenReturn(true);

            aspect.bindTenantToTransaction();

            verify(jdbcTemplate, never()).execute(anyString());
        }
    }

    @Test
    void noOpWhenContextHasNullTenantId() {
        TenantContextHolder.set(context(null));

        try (MockedStatic<TransactionSynchronizationManager> tx =
                     mockStatic(TransactionSynchronizationManager.class)) {
            tx.when(TransactionSynchronizationManager::isActualTransactionActive).thenReturn(true);

            aspect.bindTenantToTransaction();

            verify(jdbcTemplate, never()).execute(anyString());
        }
    }

    private static TenantContext context(UUID tenantId) {
        return new TenantContext(UUID.randomUUID(), tenantId,
                Set.of(), Set.of("HRLAB_PROJECT_MANAGER"), Set.of("POSITION_READ"),
                Set.of(), false, "ru-RU");
    }
}
