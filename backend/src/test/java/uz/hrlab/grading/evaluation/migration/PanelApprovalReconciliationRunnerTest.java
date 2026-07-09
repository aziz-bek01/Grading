package uz.hrlab.grading.evaluation.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.tenancy.domain.TenantStatus;
import uz.hrlab.grading.tenancy.infrastructure.TenantJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orchestration guard for the one-shot CEO-approval reconciliation runner: only
 * ACTIVE tenants are processed, the kill-switch short-circuits the whole sweep,
 * and one tenant's failure does not abort the others (fail-soft per tenant).
 */
@ExtendWith(MockitoExtension.class)
class PanelApprovalReconciliationRunnerTest {

    @Mock TenantRepository tenants;
    @Mock BackfillPanelApprovalsMigration migration;

    @Test
    void processesOnlyActiveTenants() {
        UUID activeId = UUID.randomUUID();
        TenantJpaEntity active = tenant(activeId, TenantStatus.ACTIVE);
        TenantJpaEntity provisioning = tenant(UUID.randomUUID(), TenantStatus.PROVISIONING);
        TenantJpaEntity suspended = tenant(UUID.randomUUID(), TenantStatus.SUSPENDED);
        TenantJpaEntity archived = tenant(UUID.randomUUID(), TenantStatus.ARCHIVED);
        when(tenants.findAll()).thenReturn(List.of(active, provisioning, suspended, archived));
        when(migration.runForTenant(activeId))
                .thenReturn(new BackfillPanelApprovalsMigration.Result(2, 1));

        runner(true).run(null);

        verify(migration).runForTenant(activeId);
        verify(migration, never()).runForTenant(provisioning.getId());
        verify(migration, never()).runForTenant(suspended.getId());
        verify(migration, never()).runForTenant(archived.getId());
    }

    @Test
    void killSwitchDisablesTheWholeSweep() {
        runner(false).run(null);
        verify(tenants, never()).findAll();
        verify(migration, never()).runForTenant(any());
    }

    @Test
    void oneTenantFailureDoesNotAbortOthers() {
        UUID failId = UUID.randomUUID();
        UUID okId = UUID.randomUUID();
        TenantJpaEntity failing = tenant(failId, TenantStatus.ACTIVE);
        TenantJpaEntity ok = tenant(okId, TenantStatus.ACTIVE);
        when(tenants.findAll()).thenReturn(List.of(failing, ok));
        when(migration.runForTenant(failId)).thenThrow(new RuntimeException("boom"));
        when(migration.runForTenant(okId))
                .thenReturn(new BackfillPanelApprovalsMigration.Result(0, 1));

        runner(true).run(null); // must not propagate

        verify(migration).runForTenant(failId);
        verify(migration).runForTenant(okId); // sweep continued past the failure
    }

    // --------------------------------------------------------------- helpers

    private PanelApprovalReconciliationRunner runner(boolean enabled) {
        return new PanelApprovalReconciliationRunner(tenants, migration, enabled);
    }

    private static TenantJpaEntity tenant(UUID id, TenantStatus status) {
        TenantJpaEntity t = mock(TenantJpaEntity.class);
        lenient().when(t.getId()).thenReturn(id);
        lenient().when(t.getStatus()).thenReturn(status);
        return t;
    }
}
