package uz.hrlab.grading.access.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.access.api.RolePermissionsResponse;
import uz.hrlab.grading.access.domain.RoleScope;
import uz.hrlab.grading.access.infrastructure.PermissionJpaEntity;
import uz.hrlab.grading.access.infrastructure.PermissionRepository;
import uz.hrlab.grading.access.infrastructure.RoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.RolePermissionId;
import uz.hrlab.grading.access.infrastructure.RolePermissionJpaEntity;
import uz.hrlab.grading.access.infrastructure.RolePermissionRepository;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
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
 * Offline unit tests for {@link RolePermissionAdminUseCase} (slice E2). Pure
 * Mockito — no Spring, no DB (Windows/Docker-safe). Exercises the guard order:
 * restricted → 422, caller-not-held → 422, system-role non-super-admin → 403,
 * the replace-set delta + one audit row per change, and the idempotent no-op.
 */
@Tag("unit")
class RolePermissionAdminUseCaseTest {

    private RoleRepository roleRepo;
    private PermissionRepository permissionRepo;
    private RolePermissionRepository rolePermissionRepo;
    private AuditService audit;

    private RolePermissionAdminUseCase useCase;

    private final UUID roleId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    // Catalog permission rows used across tests.
    private final PermissionJpaEntity projectRead =
            new PermissionJpaEntity(UUID.randomUUID(), "PROJECT_READ", "PROJECT", "READ", null);
    private final PermissionJpaEntity projectEdit =
            new PermissionJpaEntity(UUID.randomUUID(), "PROJECT_EDIT", "PROJECT", "EDIT", null);
    private final PermissionJpaEntity orgRead =
            new PermissionJpaEntity(UUID.randomUUID(), "ORG_READ", "ORG", "READ", null);
    private final PermissionJpaEntity salaryView =
            new PermissionJpaEntity(UUID.randomUUID(), "SALARY_VIEW", "SALARY", "VIEW", null);

    private final RoleJpaEntity role =
            new RoleJpaEntity(roleId, "CLIENT_HR_DIRECTOR", "Client HR Director", RoleScope.TENANT);

    @BeforeEach
    void setUp() {
        roleRepo = mock(RoleRepository.class);
        permissionRepo = mock(PermissionRepository.class);
        rolePermissionRepo = mock(RolePermissionRepository.class);
        audit = mock(AuditService.class);
        useCase = new RolePermissionAdminUseCase(roleRepo, permissionRepo, rolePermissionRepo, audit);

        when(roleRepo.findById(roleId)).thenReturn(Optional.of(role));
        when(permissionRepo.findByCode("PROJECT_READ")).thenReturn(Optional.of(projectRead));
        when(permissionRepo.findByCode("PROJECT_EDIT")).thenReturn(Optional.of(projectEdit));
        when(permissionRepo.findByCode("ORG_READ")).thenReturn(Optional.of(orgRead));
        when(permissionRepo.findByCode("SALARY_VIEW")).thenReturn(Optional.of(salaryView));
        when(permissionRepo.findById(projectRead.getId())).thenReturn(Optional.of(projectRead));
        when(permissionRepo.findById(projectEdit.getId())).thenReturn(Optional.of(projectEdit));
        when(permissionRepo.findById(orgRead.getId())).thenReturn(Optional.of(orgRead));

        // Default caller: HRLAB_SUPER_ADMIN — holds USER_ROLE_ASSIGN_HRLAB (system-role
        // edit gate) plus the business permissions used in the happy-path tests.
        setCaller(Set.of(
                PermissionCodes.USER_ROLE_ASSIGN_HRLAB,
                PermissionCodes.USER_ACCESS_MANAGE,
                "PROJECT_READ", "PROJECT_EDIT", "ORG_READ"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private void setCaller(Set<String> permissions) {
        TenantContextHolder.set(new TenantContext(
                actorId, tenantId, Set.of(), Set.of(), permissions, Set.of(), false, "ru-RU"));
    }

    private RolePermissionJpaEntity grant(PermissionJpaEntity p) {
        return new RolePermissionJpaEntity(new RolePermissionId(roleId, p.getId()));
    }

    // ----------------------------------------------------------------- (a) 404
    @Test
    void unknownRoleIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(roleRepo.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.replaceRolePermissions(missing, List.of("PROJECT_READ")))
                .isInstanceOf(TenantAccessDeniedException.class); // → 404, no reveal

        verify(rolePermissionRepo, never()).save(any());
        verify(audit, never()).record(any());
    }

    // ----------------------------------------------------------------- (b) 403
    @Test
    void systemRoleEditByNonSuperAdminReturns403() {
        // Caller holds USER_ACCESS_MANAGE (passes @PreAuthorize) but NOT
        // USER_ROLE_ASSIGN_HRLAB — cannot edit a system role.
        setCaller(Set.of(PermissionCodes.USER_ACCESS_MANAGE, "PROJECT_READ"));

        assertThatThrownBy(() -> useCase.replaceRolePermissions(roleId, List.of("PROJECT_READ")))
                .isInstanceOf(PermissionDeniedException.class); // → 403

        verify(rolePermissionRepo, never()).save(any());
        verify(rolePermissionRepo, never()).delete(any());
        verify(audit, never()).record(any());
    }

    // ----------------------------------------------------------------- (c) 422 restricted
    @Test
    void requestingRestrictedPermissionReturns422() {
        assertThatThrownBy(() ->
                useCase.replaceRolePermissions(roleId, List.of("PROJECT_READ", "SALARY_VIEW")))
                .isInstanceOf(UnprocessableEntityException.class)
                .extracting("code").isEqualTo("PERMISSION_RESTRICTED");

        verify(rolePermissionRepo, never()).save(any());
        verify(audit, never()).record(any());
    }

    // ----------------------------------------------------------------- (d) 422 not-held
    @Test
    void requestingPermissionCallerDoesNotHoldReturns422() {
        // Caller is super admin (passes system-role gate) but does NOT hold ORG_READ.
        setCaller(Set.of(PermissionCodes.USER_ROLE_ASSIGN_HRLAB,
                PermissionCodes.USER_ACCESS_MANAGE, "PROJECT_READ"));

        assertThatThrownBy(() ->
                useCase.replaceRolePermissions(roleId, List.of("PROJECT_READ", "ORG_READ")))
                .isInstanceOf(UnprocessableEntityException.class)
                .extracting("code").isEqualTo("PERMISSION_NOT_HELD_BY_CALLER");

        verify(rolePermissionRepo, never()).save(any());
        verify(audit, never()).record(any());
    }

    // ------------------------------------------------------- (e) replace-set delta + audit
    @Test
    void replaceSetInsertsAddedDeletesRemovedAndAuditsEachChange() {
        // Current grants: PROJECT_READ (keep) + ORG_READ (drop).
        when(rolePermissionRepo.findAllByIdRoleId(roleId))
                .thenReturn(List.of(grant(projectRead), grant(orgRead)));
        // After-state read by getRolePermissions() (return value); irrelevant to asserts.
        when(permissionRepo.findAll()).thenReturn(List.of(projectRead, projectEdit, orgRead));

        // Desired: PROJECT_READ (unchanged) + PROJECT_EDIT (added). ORG_READ dropped.
        RolePermissionsResponse out =
                useCase.replaceRolePermissions(roleId, List.of("PROJECT_READ", "PROJECT_EDIT"));
        assertThat(out.roleCode()).isEqualTo("CLIENT_HR_DIRECTOR");

        // One insert (PROJECT_EDIT), one delete (ORG_READ); PROJECT_READ untouched.
        ArgumentCaptor<RolePermissionJpaEntity> saved =
                ArgumentCaptor.forClass(RolePermissionJpaEntity.class);
        verify(rolePermissionRepo, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).hasSize(1);
        assertThat(saved.getValue().getId().getPermissionId()).isEqualTo(projectEdit.getId());

        ArgumentCaptor<RolePermissionJpaEntity> deleted =
                ArgumentCaptor.forClass(RolePermissionJpaEntity.class);
        verify(rolePermissionRepo, atLeastOnce()).delete(deleted.capture());
        assertThat(deleted.getAllValues()).hasSize(1);
        assertThat(deleted.getValue().getId().getPermissionId()).isEqualTo(orgRead.getId());

        // Exactly two audit rows: one GRANTED, one REVOKED.
        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, atLeastOnce()).record(events.capture());
        List<String> actions = events.getAllValues().stream().map(AuditEvent::action).toList();
        assertThat(actions).containsExactlyInAnyOrder(
                AuditAction.ROLE_PERMISSION_GRANTED, AuditAction.ROLE_PERMISSION_REVOKED);
    }

    // ----------------------------------------------------------------- (e) idempotent no-op
    @Test
    void unchangedSetIsNoOpAndAuditsNothing() {
        when(rolePermissionRepo.findAllByIdRoleId(roleId))
                .thenReturn(List.of(grant(projectRead), grant(projectEdit)));
        when(permissionRepo.findAll()).thenReturn(List.of(projectRead, projectEdit, orgRead));

        useCase.replaceRolePermissions(roleId, List.of("PROJECT_READ", "PROJECT_EDIT"));

        verify(rolePermissionRepo, never()).save(any());
        verify(rolePermissionRepo, never()).delete(any());
        verify(audit, never()).record(any());
    }

    // ------------------------------------------------------------------- GET matrix
    @Test
    void getReturnsFullCatalogWithGrantedAndRestrictedFlags() {
        when(rolePermissionRepo.findAllByIdRoleId(roleId)).thenReturn(List.of(grant(projectRead)));
        when(permissionRepo.findAll())
                .thenReturn(List.of(projectRead, projectEdit, orgRead, salaryView));

        RolePermissionsResponse out = useCase.getRolePermissions(roleId);

        assertThat(out.roleCode()).isEqualTo("CLIENT_HR_DIRECTOR");
        assertThat(out.scope()).isEqualTo("TENANT");
        assertThat(out.isSystem()).isTrue();
        assertThat(out.editableByCaller()).isTrue(); // default caller is super admin
        assertThat(out.items()).hasSize(4);

        var projectReadItem = out.items().stream()
                .filter(i -> i.code().equals("PROJECT_READ")).findFirst().orElseThrow();
        assertThat(projectReadItem.granted()).isTrue();
        assertThat(projectReadItem.restricted()).isFalse();

        var salaryItem = out.items().stream()
                .filter(i -> i.code().equals("SALARY_VIEW")).findFirst().orElseThrow();
        assertThat(salaryItem.granted()).isFalse();
        assertThat(salaryItem.restricted()).isTrue();
    }

    @Test
    void getMarksMatrixReadOnlyForNonSuperAdminOnSystemRole() {
        setCaller(Set.of(PermissionCodes.USER_ACCESS_MANAGE));
        when(rolePermissionRepo.findAllByIdRoleId(roleId)).thenReturn(List.of());
        when(permissionRepo.findAll()).thenReturn(List.of(projectRead));

        RolePermissionsResponse out = useCase.getRolePermissions(roleId);

        assertThat(out.editableByCaller()).isFalse();
    }
}
