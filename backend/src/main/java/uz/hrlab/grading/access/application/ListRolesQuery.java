package uz.hrlab.grading.access.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.api.RoleResponse;
import uz.hrlab.grading.access.application.UserManagementPolicy.RoleAssignDenialReason;
import uz.hrlab.grading.access.infrastructure.RoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;
import uz.hrlab.grading.tenancy.domain.Locale;

import java.util.List;
import java.util.Map;

/**
 * Query handler for {@code GET /api/v1/roles[?assignableOnly=true]} (slice E1).
 *
 * <p>Serves the data-driven, scope-aware role catalog so the frontend stops
 * hardcoding which roles are grantable. For each role the handler computes
 * {@code assignable_by_caller} from {@link UserManagementPolicy#canAssignRole}
 * — the SAME predicate that gates the actual assignment in
 * {@link AssignRoleUseCase} — plus a stable {@code reason_if_not} explaining a
 * denial.
 *
 * <p>Tenant scope is derived from {@link TenantContext} only (never the request).
 * Since slice E3 the catalog returns the SYSTEM roles PLUS the active tenant's
 * own custom roles ({@code is_system = true OR tenant_id = ctx.tenantId()});
 * other tenants' custom roles are invisible. {@code is_system}/{@code is_custom}
 * are read from the entity ({@code is_custom = !is_system}). Custom roles are
 * TENANT-scoped, so {@link UserManagementPolicy#canAssignRole} treats them as
 * assignable by the owning tenant admin (only HRLAB_/PLATFORM-scope roles are
 * gated), and they flow through {@link AssignRoleUseCase} like any other role.
 */
@Service
public class ListRolesQuery {

    private final UserManagementPolicy policy;
    private final RoleRepository roleRepo;

    public ListRolesQuery(UserManagementPolicy policy, RoleRepository roleRepo) {
        this.policy = policy;
        this.roleRepo = roleRepo;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list(boolean assignableOnly) {
        TenantContext ctx = TenantContextHolder.requireActive();

        return roleRepo.findByIsSystemTrueOrTenantIdOrderByScopeAscCodeAsc(ctx.tenantId()).stream()
                .map(role -> toResponse(ctx, role))
                .filter(r -> !assignableOnly || r.assignableByCaller())
                .toList();
    }

    private RoleResponse toResponse(TenantContext ctx, RoleJpaEntity role) {
        RoleAssignDenialReason reason = policy.roleAssignDenialReason(ctx, role);
        boolean assignable = reason == null;
        return new RoleResponse(
                role.getId(),
                role.getCode(),
                nameI18n(role.getName()),
                role.getScope() == null ? null : role.getScope().name(),
                role.isSystem(),
                !role.isSystem(),
                assignable,
                reason == null ? null : reason.name());
    }

    /**
     * Builds the {@code name_i18n} map. The {@code roles} table carries a single
     * {@code name} column (per-locale translation rows are a later slice), so the
     * same display name is emitted for every supported locale — the response shape
     * is already the final i18n contract, only the values are not yet localized.
     */
    private static Map<String, String> nameI18n(String name) {
        String value = name == null ? "" : name;
        return Map.of(
                Locale.RU_RU, value,
                Locale.UZ_CYRL_UZ, value,
                Locale.UZ_LATN_UZ, value,
                Locale.EN_US, value);
    }
}
