package uz.hrlab.grading.access.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.access.api.UserScopeResponse;
import uz.hrlab.grading.access.infrastructure.UserProjectAssignmentJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserProjectAssignmentRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Offline unit tests for {@link SetUserProjectAssignmentsUseCase} (E4-S1).
 * Pure Mockito. Mirrors the department-scope assignment test: tenant-ownership
 * validation (via the access-owned {@link ProjectReferencePort#existsInTenant}),
 * replace-set semantics, and one audit row per change.
 */
@Tag("unit")
class SetUserProjectAssignmentsUseCaseTest {

    private UserManagementPolicy policy;
    private UserTenantMembershipRepository membershipRepo;
    private UserProjectAssignmentRepository assignmentRepo;
    private ProjectReferencePort projectReference;
    private GetUserScopesQuery scopesQuery;
    private AuditService audit;

    private SetUserProjectAssignmentsUseCase useCase;

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        policy = mock(UserManagementPolicy.class);
        membershipRepo = mock(UserTenantMembershipRepository.class);
        assignmentRepo = mock(UserProjectAssignmentRepository.class);
        projectReference = mock(ProjectReferencePort.class);
        scopesQuery = mock(GetUserScopesQuery.class);
        audit = mock(AuditService.class);
        useCase = new SetUserProjectAssignmentsUseCase(
                policy, membershipRepo, assignmentRepo, projectReference, scopesQuery, audit);

        doNothing().when(policy).requireCanManageInTenant(any(), any());
        // Target is a MEMBER of the tenant by default (P2-1 happy path).
        when(membershipRepo.findByUserIdAndTenantId(userId, tenantId))
                .thenReturn(Optional.of(mock(UserTenantMembershipJpaEntity.class)));
        when(scopesQuery.byUserAndTenant(userId, tenantId))
                .thenReturn(new UserScopeResponse(userId, tenantId, List.of(), List.of()));

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

        verify(projectReference, never()).existsInTenant(any(), any());
        verify(assignmentRepo, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void rejectsForeignProjectBeforeAnyWrite() {
        UUID foreign = UUID.randomUUID();
        when(projectReference.existsInTenant(foreign, tenantId)).thenReturn(false);

        assertThatThrownBy(() -> useCase.replace(userId, tenantId, List.of(foreign)))
                .isInstanceOf(ValidationException.class);

        verify(assignmentRepo, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void replaceSetAddsNewRemovesDroppedKeepsUnchanged() {
        UUID keep = UUID.randomUUID();
        UUID add = UUID.randomUUID();
        UUID drop = UUID.randomUUID();

        when(projectReference.existsInTenant(eq(keep), eq(tenantId))).thenReturn(true);
        when(projectReference.existsInTenant(eq(add), eq(tenantId))).thenReturn(true);

        UserProjectAssignmentJpaEntity keepRow = new UserProjectAssignmentJpaEntity(
                UUID.randomUUID(), userId, tenantId, keep,
                UserProjectAssignmentJpaEntity.STATUS_ACTIVE);
        UserProjectAssignmentJpaEntity dropRow = new UserProjectAssignmentJpaEntity(
                UUID.randomUUID(), userId, tenantId, drop,
                UserProjectAssignmentJpaEntity.STATUS_ACTIVE);
        when(assignmentRepo.findAllByUserIdAndTenantId(userId, tenantId))
                .thenReturn(List.of(keepRow, dropRow));

        useCase.replace(userId, tenantId, List.of(keep, add));

        assertThat(dropRow.getStatus()).isEqualTo(UserProjectAssignmentJpaEntity.STATUS_REVOKED);

        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, atLeastOnce()).record(events.capture());
        List<String> actions = events.getAllValues().stream().map(AuditEvent::action).toList();
        assertThat(actions).contains(
                AuditAction.USER_PROJECT_ASSIGNMENT_ADDED,
                AuditAction.USER_PROJECT_ASSIGNMENT_REMOVED);
        long adds = actions.stream()
                .filter(AuditAction.USER_PROJECT_ASSIGNMENT_ADDED::equals).count();
        assertThat(adds).isEqualTo(1);
    }

    @Test
    void emptyDesiredSetRevokesAllActiveAndSkipsOwnershipQuery() {
        UUID drop = UUID.randomUUID();
        UserProjectAssignmentJpaEntity dropRow = new UserProjectAssignmentJpaEntity(
                UUID.randomUUID(), userId, tenantId, drop,
                UserProjectAssignmentJpaEntity.STATUS_ACTIVE);
        when(assignmentRepo.findAllByUserIdAndTenantId(userId, tenantId)).thenReturn(List.of(dropRow));

        useCase.replace(userId, tenantId, List.of());

        verify(projectReference, never()).existsInTenant(any(), any());
        assertThat(dropRow.getStatus()).isEqualTo(UserProjectAssignmentJpaEntity.STATUS_REVOKED);
    }
}
