package uz.hrlab.grading.access.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.access.api.UserScopeResponse;
import uz.hrlab.grading.access.infrastructure.UserDepartmentScopeJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserDepartmentScopeRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Offline unit tests for {@link SetUserDepartmentScopesUseCase} (E4-S1) — the
 * replace-set department-scope assignment use case. Pure Mockito.
 *
 * <p>Asserts:
 * <ul>
 *   <li>tenant-ownership validation rejects a foreign department (400) BEFORE
 *       any write;</li>
 *   <li>replace-set semantics: a new root is GRANTED, a removed root is
 *       REVOKED, an unchanged root is left alone;</li>
 *   <li>one audit row per individual change (GRANTED / REVOKED).</li>
 * </ul>
 */
@Tag("unit")
class SetUserDepartmentScopesUseCaseTest {

    private UserManagementPolicy policy;
    private UserTenantMembershipRepository membershipRepo;
    private UserDepartmentScopeRepository scopeRepo;
    private DepartmentRepository departmentRepo;
    private GetUserScopesQuery scopesQuery;
    private AuditService audit;

    private SetUserDepartmentScopesUseCase useCase;

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        policy = mock(UserManagementPolicy.class);
        membershipRepo = mock(UserTenantMembershipRepository.class);
        scopeRepo = mock(UserDepartmentScopeRepository.class);
        departmentRepo = mock(DepartmentRepository.class);
        scopesQuery = mock(GetUserScopesQuery.class);
        audit = mock(AuditService.class);
        useCase = new SetUserDepartmentScopesUseCase(
                policy, membershipRepo, scopeRepo, departmentRepo, scopesQuery, audit);

        doNothing().when(policy).requireCanManageInTenant(any(), any());
        // Target is a MEMBER of the tenant by default (P2-1 happy path).
        when(membershipRepo.findByUserIdAndTenantId(userId, tenantId))
                .thenReturn(Optional.of(mock(UserTenantMembershipJpaEntity.class)));
        when(scopesQuery.byUserAndTenant(userId, tenantId))
                .thenReturn(new UserScopeResponse(userId, tenantId, List.of(), List.of()));

        // Caller is a tenant-wide-bypass role by default (CLIENT_COMPANY_ADMIN)
        // so the P2-2 subtree constraint does not apply unless a test overrides it.
        TenantContext ctx = new TenantContext(actorId, tenantId, Set.of(),
                Set.of(RoleCodes.CLIENT_COMPANY_ADMIN), Set.of(), Set.of(), false, "ru-RU");
        TenantContextHolder.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void rejectsTargetWithNoMembershipInTenantWithoutWritingOrAuditing() {
        // P2-1: user exists globally but is NOT a member of this tenant.
        when(membershipRepo.findByUserIdAndTenantId(userId, tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.replace(userId, tenantId, List.of(UUID.randomUUID())))
                .isInstanceOf(TenantAccessDeniedException.class); // → 404, no existence reveal

        verify(departmentRepo, never()).countByIdInAndTenantId(anyCollection(), any());
        verify(scopeRepo, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void nonBypassCallerCannotGrantDepartmentOutsideOwnSubtree() {
        // P2-2: a non-bypass USER_MEMBERSHIP_MANAGE caller (DEPARTMENT_MANAGER)
        // whose own scope is {inScope} tries to grant {outOfScope}.
        UUID inScope = UUID.randomUUID();
        UUID outOfScope = UUID.randomUUID();
        TenantContext nonBypass = new TenantContext(actorId, tenantId, Set.of(),
                Set.of(RoleCodes.DEPARTMENT_MANAGER), Set.of(),
                Set.of(inScope), false, "ru-RU");
        TenantContextHolder.set(nonBypass);

        // Departments belong to the tenant — the rejection is purely the
        // subtree-bound check, not tenant ownership.
        when(departmentRepo.countByIdInAndTenantId(anyCollection(), eq(tenantId))).thenReturn(1L);

        assertThatThrownBy(() -> useCase.replace(userId, tenantId, List.of(outOfScope)))
                .isInstanceOf(TenantAccessDeniedException.class); // → 404, no department reveal

        verify(scopeRepo, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void nonBypassCallerCanGrantDepartmentInsideOwnSubtree() {
        // P2-2 positive: a department within the caller's own scope is allowed.
        UUID inScope = UUID.randomUUID();
        TenantContext nonBypass = new TenantContext(actorId, tenantId, Set.of(),
                Set.of(RoleCodes.DEPARTMENT_MANAGER), Set.of(),
                Set.of(inScope), false, "ru-RU");
        TenantContextHolder.set(nonBypass);

        when(departmentRepo.countByIdInAndTenantId(anyCollection(), eq(tenantId))).thenReturn(1L);
        when(scopeRepo.findAllByUserIdAndTenantId(userId, tenantId)).thenReturn(List.of());

        useCase.replace(userId, tenantId, List.of(inScope));

        verify(scopeRepo).save(any());
    }

    @Test
    void rejectsForeignDepartmentBeforeAnyWrite() {
        UUID foreign = UUID.randomUUID();
        // Only 0 of 1 requested departments belong to the tenant.
        when(departmentRepo.countByIdInAndTenantId(anyCollection(), eq(tenantId))).thenReturn(0L);

        assertThatThrownBy(() -> useCase.replace(userId, tenantId, List.of(foreign)))
                .isInstanceOf(ValidationException.class);

        verify(scopeRepo, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void replaceSetGrantsNewRevokesRemovedKeepsUnchanged() {
        UUID keep = UUID.randomUUID();   // already ACTIVE, stays
        UUID add = UUID.randomUUID();    // new → GRANTED
        UUID drop = UUID.randomUUID();   // existing ACTIVE, not desired → REVOKED

        when(departmentRepo.countByIdInAndTenantId(anyCollection(), eq(tenantId)))
                .thenReturn(2L); // keep + add both valid

        UserDepartmentScopeJpaEntity keepRow = new UserDepartmentScopeJpaEntity(
                UUID.randomUUID(), userId, tenantId, keep,
                UserDepartmentScopeJpaEntity.STATUS_ACTIVE, actorId);
        UserDepartmentScopeJpaEntity dropRow = new UserDepartmentScopeJpaEntity(
                UUID.randomUUID(), userId, tenantId, drop,
                UserDepartmentScopeJpaEntity.STATUS_ACTIVE, actorId);
        when(scopeRepo.findAllByUserIdAndTenantId(userId, tenantId))
                .thenReturn(List.of(keepRow, dropRow));

        useCase.replace(userId, tenantId, List.of(keep, add));

        // 'add' inserted ACTIVE, 'drop' flipped to REVOKED, 'keep' untouched.
        assertThat(dropRow.getStatus()).isEqualTo(UserDepartmentScopeJpaEntity.STATUS_REVOKED);

        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, atLeastOnce()).record(events.capture());
        List<String> actions = events.getAllValues().stream().map(AuditEvent::action).toList();
        assertThat(actions).contains(
                AuditAction.USER_DEPARTMENT_SCOPE_GRANTED,
                AuditAction.USER_DEPARTMENT_SCOPE_REVOKED);
        // 'keep' produced no audit row (no GRANT for the unchanged root).
        long grants = actions.stream()
                .filter(AuditAction.USER_DEPARTMENT_SCOPE_GRANTED::equals).count();
        assertThat(grants).isEqualTo(1);
    }

    @Test
    void emptyDesiredSetRevokesAllActive() {
        UUID drop = UUID.randomUUID();
        UserDepartmentScopeJpaEntity dropRow = new UserDepartmentScopeJpaEntity(
                UUID.randomUUID(), userId, tenantId, drop,
                UserDepartmentScopeJpaEntity.STATUS_ACTIVE, actorId);
        when(scopeRepo.findAllByUserIdAndTenantId(userId, tenantId)).thenReturn(List.of(dropRow));

        useCase.replace(userId, tenantId, List.of());

        // No ownership query for an empty set; the one ACTIVE row is revoked.
        verify(departmentRepo, never()).countByIdInAndTenantId(anyCollection(), any());
        assertThat(dropRow.getStatus()).isEqualTo(UserDepartmentScopeJpaEntity.STATUS_REVOKED);
    }
}
