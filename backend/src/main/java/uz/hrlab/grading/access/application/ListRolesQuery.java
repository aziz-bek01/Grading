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
 * Today the catalog is tenant-agnostic (all roles are seeded system roles); the
 * active tenant is still resolved so that, once slice E3 adds tenant-scoped
 * custom roles, the repository finder can filter by it without changing this
 * handler's mapping.
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

        return roleRepo.findAllByOrderByScopeAscCodeAsc().stream()
                .map(role -> toResponse(ctx, role))
                .filter(r -> !assignableOnly || r.assignableByCaller())
                .toList();
    }

    private RoleResponse toResponse(TenantContext ctx, RoleJpaEntity role) {
        RoleAssignDenialReason reason = policy.roleAssignDenialReason(ctx, role);
        boolean assignable = reason == null;
        return new RoleResponse(
                role.getCode(),
                nameI18n(role.getName()),
                role.getScope() == null ? null : role.getScope().name(),
                /* isSystem */ true,
                /* isCustom */ false,
                assignable,
                reason == null ? null : reason.name());
    }

    /**
     * Builds the {@code name_i18n} map. The {@code roles} table currently has a
     * single {@code name} column (translations land in slice E3), so the same
     * display name is emitted for every supported locale — the response shape is
     * already the final i18n contract, only the values are not yet localized.
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
