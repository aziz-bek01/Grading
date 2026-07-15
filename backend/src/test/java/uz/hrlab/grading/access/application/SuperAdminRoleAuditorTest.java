package uz.hrlab.grading.access.application;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Task #6 — the {@link SuperAdminRoleAuditor} choke point. Proves that:
 * <ol>
 *   <li>granting {@code HRLAB_SUPER_ADMIN} emits {@link AuditAction#PLATFORM_SUPER_ADMIN_GRANTED}
 *       with the right actor (who did it) + target (who received it);</li>
 *   <li>revoking it emits {@link AuditAction#PLATFORM_SUPER_ADMIN_REVOKED};</li>
 *   <li>a normal (non-super-admin) role change emits NEITHER (NO-OP) — so the
 *       existing generic role auditing is never double-counted.</li>
 * </ol>
 */
@Tag("security")
class SuperAdminRoleAuditorTest {

    private static final UUID ACTOR   = UUID.randomUUID();
    private static final UUID TENANT  = UUID.randomUUID();
    private static final UUID TARGET  = UUID.randomUUID();
    private static final UUID USER_ROLE_ID = UUID.randomUUID();

    private final AuditService audit = mock(AuditService.class);
    private final SuperAdminRoleAuditor auditor = new SuperAdminRoleAuditor(audit);

    @Test
    void grantingSuperAdminEmitsGrantedWithActorAndTarget() {
        auditor.onRoleGranted(ACTOR, TENANT, TARGET,
                RoleCodes.HRLAB_SUPER_ADMIN, USER_ROLE_ID, "assign-role");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        AuditEvent e = captor.getValue();
        assertThat(e.action()).isEqualTo(AuditAction.PLATFORM_SUPER_ADMIN_GRANTED);
        assertThat(e.actorUserId()).isEqualTo(ACTOR);   // who did it
        assertThat(e.entityId()).isEqualTo(TARGET);      // who received it
        assertThat(e.entityType()).isEqualTo("User");
        assertThat(e.tenantId()).isEqualTo(TENANT);
        assertThat(e.reason()).contains(USER_ROLE_ID.toString()).contains("assign-role");
    }

    @Test
    void revokingSuperAdminEmitsRevoked() {
        auditor.onRoleRevoked(ACTOR, TENANT, TARGET,
                RoleCodes.HRLAB_SUPER_ADMIN, USER_ROLE_ID, "remove-role");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        AuditEvent e = captor.getValue();
        assertThat(e.action()).isEqualTo(AuditAction.PLATFORM_SUPER_ADMIN_REVOKED);
        assertThat(e.actorUserId()).isEqualTo(ACTOR);
        assertThat(e.entityId()).isEqualTo(TARGET);
    }

    @Test
    void normalRoleGrantDoesNotEmitSuperAdminEvent() {
        auditor.onRoleGranted(ACTOR, TENANT, TARGET,
                RoleCodes.CLIENT_HR_SPECIALIST, USER_ROLE_ID, "assign-role");
        verifyNoInteractions(audit);
    }

    @Test
    void normalRoleRevokeDoesNotEmitSuperAdminEvent() {
        auditor.onRoleRevoked(ACTOR, TENANT, TARGET,
                RoleCodes.DEPARTMENT_MANAGER, USER_ROLE_ID, "remove-role");
        verifyNoInteractions(audit);
    }

    @Test
    void nullRoleCodeIsANoOp() {
        auditor.onRoleRevoked(ACTOR, TENANT, TARGET, null, USER_ROLE_ID, "remove-role");
        verify(audit, never()).record(org.mockito.ArgumentMatchers.any());
    }
}
