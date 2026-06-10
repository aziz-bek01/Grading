package uz.hrlab.grading.access.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.access.api.RoleResponse;
import uz.hrlab.grading.access.domain.RoleScope;
import uz.hrlab.grading.access.infrastructure.PermissionJpaEntity;
import uz.hrlab.grading.access.infrastructure.PermissionRepository;
import uz.hrlab.grading.access.infrastructure.RoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.RolePermissionJpaEntity;
import uz.hrlab.grading.access.infrastructure.RolePermissionRepository;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
import uz.hrlab.grading.access.infrastructure.UserRoleRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.ConflictException;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.UnprocessableEntityException;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Offline unit tests for {@link CustomRoleUseCase} (slice E3). Pure Mockito —
 * no Spring, no DB (Windows/Docker-safe). Proves the create/edit/delete guards:
 * reserved-system-code (409), in-tenant duplicate (409), restricted (422),
 * caller-not-held (422), cross-tenant 404, system-role 403, in-use 409, plus the
 * happy paths and their audit trail.
 */
@Tag("unit")
class CustomRoleUseCaseTest {

    private RoleRepository roleRepo;
    private PermissionRepository permissionRepo;
    private RolePermissionRepository rolePermissionRepo;
    private UserRoleRepository userRoleRepo;
    private AuditService audit;

    private CustomRoleUseCase useCase;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID otherTenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private final PermissionJpaEntity projectRead =
            new PermissionJpaEntity(UUID.randomUUID(), "PROJECT_READ", "PROJECT", "READ", null);
    private final PermissionJpaEntity evaluationRead =
            new PermissionJpaEntity(UUID.randomUUID(), "EVALUATION_READ", "EVALUATION", "READ", null);

    @BeforeEach
    void setUp() {
        roleRepo = mock(RoleRepository.class);
        permissionRepo = mock(PermissionRepository.class);
        rolePermissionRepo = mock(RolePermissionRepository.class);
        userRoleRepo = mock(UserRoleRepository.class);
        audit = mock(AuditService.class);
        RolePermissionGuard guard = new RolePermissionGuard(permissionRepo);
        RoleOwnershipGuard ownershipGuard = new RoleOwnershipGuard();
        UserManagementPolicy policy =
                new UserManagementPolicy(mock(UserTenantMembershipRepository.class));
        useCase = new CustomRoleUseCase(roleRepo, permissionRepo, rolePermissionRepo,
                userRoleRepo, guard, ownershipGuard, policy, audit);

        when(permissionRepo.findByCode("PROJECT_READ")).thenReturn(Optional.of(projectRead));
        when(permissionRepo.findByCode("EVALUATION_READ")).thenReturn(Optional.of(evaluationRead));
        when(permissionRepo.findById(projectRead.getId())).thenReturn(Optional.of(projectRead));
        when(permissionRepo.findById(evaluationRead.getId())).thenReturn(Optional.of(evaluationRead));

        // Default caller: tenant admin who HOLDS the business permissions used in
        // happy-path grants + the access-manage gate (controller @PreAuthorize).
        setCaller(Set.of(PermissionCodes.USER_ACCESS_MANAGE, "PROJECT_READ", "EVALUATION_READ"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private void setCaller(Set<String> permissions) {
        TenantContextHolder.set(new TenantContext(
                actorId, tenantId, Set.of(), Set.of(RoleCodes.CLIENT_COMPANY_ADMIN),
                permissions, Set.of(), false, "ru-RU", Set.of(tenantId)));
    }

    private RoleJpaEntity customRole(UUID id, UUID owner, String code) {
        return RoleJpaEntity.newCustom(id, owner, code, code, null);
    }

    // =====================================================================
    //  CREATE
    // =====================================================================

    @Test
    void createRejectsReservedSystemCodeCaseInsensitive() {
        // "viewer" collides with the system role VIEWER (case-insensitive) → 409.
        assertThatThrownBy(() ->
                useCase.create("viewer", null, "Viewer", List.of("PROJECT_READ")))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("ROLE_CODE_RESERVED");

        verify(roleRepo, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void createRejectsInTenantDuplicateCode() {
        when(roleRepo.findByTenantIdAndCode(tenantId, "REVIEWER"))
                .thenReturn(Optional.of(customRole(UUID.randomUUID(), tenantId, "REVIEWER")));

        assertThatThrownBy(() ->
                useCase.create("REVIEWER", null, "Reviewer", List.of("PROJECT_READ")))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("ROLE_CODE_EXISTS");

        verify(roleRepo, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void createRejectsRestrictedPermission() {
        when(roleRepo.findByTenantIdAndCode(tenantId, "REVIEWER")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                useCase.create("REVIEWER", null, "Reviewer",
                        List.of("PROJECT_READ", "SALARY_VIEW")))
                .isInstanceOf(UnprocessableEntityException.class)
                .extracting("code").isEqualTo("PERMISSION_RESTRICTED");

        verify(roleRepo, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void createRejectsPermissionCallerDoesNotHold() {
        when(roleRepo.findByTenantIdAndCode(tenantId, "REVIEWER")).thenReturn(Optional.empty());
        // Caller does NOT hold EVALUATION_READ.
        setCaller(Set.of(PermissionCodes.USER_ACCESS_MANAGE, "PROJECT_READ"));

        assertThatThrownBy(() ->
                useCase.create("REVIEWER", null, "Reviewer",
                        List.of("PROJECT_READ", "EVALUATION_READ")))
                .isInstanceOf(UnprocessableEntityException.class)
                .extracting("code").isEqualTo("PERMISSION_NOT_HELD_BY_CALLER");

        verify(roleRepo, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void createHappyPathPersistsRoleAndGrantsAndAudits() {
        when(roleRepo.findByTenantIdAndCode(tenantId, "REVIEWER")).thenReturn(Optional.empty());

        RoleResponse out = useCase.create("REVIEWER", null, "Reviewer",
                List.of("PROJECT_READ", "EVALUATION_READ"));

        assertThat(out.code()).isEqualTo("REVIEWER");
        assertThat(out.isCustom()).isTrue();
        assertThat(out.isSystem()).isFalse();
        assertThat(out.scope()).isEqualTo("TENANT");
        // TENANT custom role assignable by this tenant admin.
        assertThat(out.assignableByCaller()).isTrue();

        // Role persisted as custom, tenant-owned.
        ArgumentCaptor<RoleJpaEntity> savedRole = ArgumentCaptor.forClass(RoleJpaEntity.class);
        verify(roleRepo).save(savedRole.capture());
        assertThat(savedRole.getValue().isSystem()).isFalse();
        assertThat(savedRole.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(savedRole.getValue().getScope()).isEqualTo(RoleScope.TENANT);

        // Two permission grants persisted.
        verify(rolePermissionRepo, atLeastOnce()).save(any(RolePermissionJpaEntity.class));

        // Audit: ROLE_CREATED + 2× ROLE_PERMISSION_GRANTED.
        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, atLeastOnce()).record(events.capture());
        List<String> actions = events.getAllValues().stream().map(AuditEvent::action).toList();
        assertThat(actions).contains(AuditAction.ROLE_CREATED);
        assertThat(actions.stream().filter(AuditAction.ROLE_PERMISSION_GRANTED::equals).count())
                .isEqualTo(2);
    }

    // =====================================================================
    //  EDIT
    // =====================================================================

    @Test
    void editCrossTenantTargetIsNotFound() {
        UUID roleId = UUID.randomUUID();
        // Not a system role; tenant-scoped lookup for THIS tenant returns empty
        // (the role belongs to another tenant) → 404, no reveal.
        when(roleRepo.findById(roleId))
                .thenReturn(Optional.of(customRole(roleId, otherTenantId, "REVIEWER")));
        when(roleRepo.findByIdAndTenantId(roleId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                useCase.edit(roleId, null, "Renamed", List.of("PROJECT_READ")))
                .isInstanceOf(TenantAccessDeniedException.class);

        verify(audit, never()).record(any());
    }

    @Test
    void editSystemRoleIsForbidden() {
        UUID roleId = UUID.randomUUID();
        when(roleRepo.findById(roleId)).thenReturn(Optional.of(
                new RoleJpaEntity(roleId, RoleCodes.VIEWER, "Viewer", RoleScope.TENANT)));

        assertThatThrownBy(() ->
                useCase.edit(roleId, null, "Renamed", List.of("PROJECT_READ")))
                .isInstanceOf(PermissionDeniedException.class);

        verify(audit, never()).record(any());
    }

    @Test
    void editGoesThroughTheSameRestrictedGuard() {
        UUID roleId = UUID.randomUUID();
        RoleJpaEntity role = customRole(roleId, tenantId, "REVIEWER");
        when(roleRepo.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRepo.findByIdAndTenantId(roleId, tenantId)).thenReturn(Optional.of(role));

        assertThatThrownBy(() ->
                useCase.edit(roleId, null, null, List.of("AUDIT_READ")))
                .isInstanceOf(UnprocessableEntityException.class)
                .extracting("code").isEqualTo("PERMISSION_RESTRICTED");

        verify(rolePermissionRepo, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void editReplaceSetAppliesDeltaAndAudits() {
        UUID roleId = UUID.randomUUID();
        RoleJpaEntity role = customRole(roleId, tenantId, "REVIEWER");
        when(roleRepo.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRepo.findByIdAndTenantId(roleId, tenantId)).thenReturn(Optional.of(role));
        // Currently grants EVALUATION_READ (will be dropped); desired is PROJECT_READ.
        when(rolePermissionRepo.findAllByIdRoleId(roleId))
                .thenReturn(List.of(new RolePermissionJpaEntity(
                        new uz.hrlab.grading.access.infrastructure.RolePermissionId(
                                roleId, evaluationRead.getId()))));

        useCase.edit(roleId, null, null, List.of("PROJECT_READ"));

        // One insert (PROJECT_READ) + one delete (EVALUATION_READ).
        verify(rolePermissionRepo, atLeastOnce()).save(any());
        verify(rolePermissionRepo, atLeastOnce()).delete(any());

        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, atLeastOnce()).record(events.capture());
        List<String> actions = events.getAllValues().stream().map(AuditEvent::action).toList();
        assertThat(actions).contains(
                AuditAction.ROLE_PERMISSION_GRANTED,
                AuditAction.ROLE_PERMISSION_REVOKED,
                AuditAction.ROLE_UPDATED);
    }

    // =====================================================================
    //  DELETE
    // =====================================================================

    @Test
    void deleteInUseRoleReturns409() {
        UUID roleId = UUID.randomUUID();
        RoleJpaEntity role = customRole(roleId, tenantId, "REVIEWER");
        when(roleRepo.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRepo.findByIdAndTenantId(roleId, tenantId)).thenReturn(Optional.of(role));
        when(userRoleRepo.existsByRoleId(roleId)).thenReturn(true);

        assertThatThrownBy(() -> useCase.delete(roleId))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("ROLE_IN_USE");

        verify(roleRepo, never()).delete(any());
        verify(rolePermissionRepo, never()).delete(any());
        verify(audit, never()).record(any());
    }

    @Test
    void deleteSystemRoleIsForbidden() {
        UUID roleId = UUID.randomUUID();
        when(roleRepo.findById(roleId)).thenReturn(Optional.of(
                new RoleJpaEntity(roleId, RoleCodes.VIEWER, "Viewer", RoleScope.TENANT)));

        assertThatThrownBy(() -> useCase.delete(roleId))
                .isInstanceOf(PermissionDeniedException.class);

        verify(roleRepo, never()).delete(any());
        verify(audit, never()).record(any());
    }

    @Test
    void deleteHappyPathRemovesGrantsThenRoleAndAudits() {
        UUID roleId = UUID.randomUUID();
        RoleJpaEntity role = customRole(roleId, tenantId, "REVIEWER");
        when(roleRepo.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRepo.findByIdAndTenantId(roleId, tenantId)).thenReturn(Optional.of(role));
        when(userRoleRepo.existsByRoleId(roleId)).thenReturn(false);
        when(rolePermissionRepo.findAllByIdRoleId(roleId))
                .thenReturn(List.of(new RolePermissionJpaEntity(
                        new uz.hrlab.grading.access.infrastructure.RolePermissionId(
                                roleId, projectRead.getId()))));

        useCase.delete(roleId);

        verify(rolePermissionRepo, atLeastOnce()).delete(any());
        verify(roleRepo).delete(role);

        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, atLeastOnce()).record(events.capture());
        assertThat(events.getAllValues().stream().map(AuditEvent::action).toList())
                .contains(AuditAction.ROLE_DELETED);
    }
}
