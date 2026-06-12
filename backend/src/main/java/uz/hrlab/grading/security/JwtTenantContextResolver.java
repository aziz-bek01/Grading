package uz.hrlab.grading.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uz.hrlab.grading.access.application.UserScopeExpander;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.infrastructure.RolePermissionRepository;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
import uz.hrlab.grading.access.infrastructure.UserJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRepository;
import uz.hrlab.grading.access.infrastructure.UserRoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRoleRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maps a validated {@link Jwt} into a {@link TenantContext}.
 *
 * <p>Authoritative source of {@code tenant_id} — never trust request input
 * (security-blueprint §20.1 finding 2).
 *
 * <h3>Two token contracts, one resolver (BE-OIDC-001)</h3>
 * Real IdP (ZITADEL) access tokens carry only standard OIDC claims
 * ({@code sub}, {@code email}, ...). They do NOT carry grading's domain claims
 * ({@code active_tenant_id}, {@code roles}, {@code permissions},
 * {@code salary_data_permission}). This resolver therefore supports both:
 *
 * <ol>
 *   <li><b>Claim-first (forward compatible):</b> if the token already carries
 *       domain claims, they are honoured verbatim — this preserves the original
 *       behaviour and lets a future custom-claims IdP action take over without
 *       a code change.</li>
 *   <li><b>DB-backed (the OIDC path used today):</b> when a domain claim is
 *       absent, the resolver looks the principal up in the grading control
 *       plane by EMAIL (falling back to {@code external_idp_subject == sub}),
 *       resolves the user's tenant memberships, picks an active tenant, and
 *       expands roles + permissions exactly the way the IdP would normally bake
 *       them into the JWT. This mirrors {@link DevUserAuthorityResolver}.</li>
 * </ol>
 *
 * <p>Fail-closed: if the email maps to no grading user, the resulting context
 * has {@code userId == null}, so downstream
 * {@code TenantContextHolder.requireActive()} / {@code GetCurrentUserUseCase}
 * reject the request (401/404 with a generic message) rather than leaking.
 */
@Component
public class JwtTenantContextResolver {

    private static final Logger log = LoggerFactory.getLogger(JwtTenantContextResolver.class);

    private final UserRepository users;
    private final UserTenantMembershipRepository memberships;
    private final UserRoleRepository userRoles;
    private final RoleRepository roles;
    private final RolePermissionRepository rolePermissions;
    private final UserScopeExpander scopeExpander;

    public JwtTenantContextResolver(UserRepository users,
                                    UserTenantMembershipRepository memberships,
                                    UserRoleRepository userRoles,
                                    RoleRepository roles,
                                    RolePermissionRepository rolePermissions,
                                    UserScopeExpander scopeExpander) {
        this.users = users;
        this.memberships = memberships;
        this.userRoles = userRoles;
        this.roles = roles;
        this.rolePermissions = rolePermissions;
        this.scopeExpander = scopeExpander;
    }

    public TenantContext resolve(Jwt jwt) {
        // 1) Claim-first: honour any domain claims the IdP already minted.
        UUID claimUserId = parseUuid(jwt.getSubject());
        UUID claimTenantId = parseUuid(jwt.getClaimAsString(JwtClaimNames.ACTIVE_TENANT_ID));
        Set<UUID> claimProjectIds = parseUuidSet(jwt.getClaim(JwtClaimNames.ACTIVE_PROJECT_IDS));
        Set<String> claimRoles = parseStringSet(jwt.getClaim(JwtClaimNames.ROLES));
        Set<String> claimPermissions = parseStringSet(jwt.getClaim(JwtClaimNames.PERMISSIONS));
        Set<UUID> claimDepartmentScope = parseUuidSet(jwt.getClaim(JwtClaimNames.DEPARTMENT_SCOPE));
        boolean claimSalaryPerm = Boolean.TRUE.equals(jwt.getClaim(JwtClaimNames.SALARY_DATA_PERM));
        String claimLocale = jwt.getClaimAsString(JwtClaimNames.LOCALE);

        // 2) Resolve the grading user. With a real ZITADEL token, `sub` is the
        //    IdP user id (not a grading users.id), so claimUserId rarely matches
        //    a grading row — resolve by email, then by external_idp_subject.
        UserJpaEntity user = resolveUser(jwt, claimUserId);
        if (user == null) {
            // Authenticated by the IdP but not provisioned in grading — return a
            // userId-less context. Downstream requireActive()/GetCurrentUserUseCase
            // fail closed (generic 401/404). Do NOT leak that the email is unknown.
            log.warn("OIDC principal not provisioned in grading: subject={} emailPresent={}",
                    jwt.getSubject(), jwt.getClaimAsString(JwtClaimNames.EMAIL) != null);
            return new TenantContext(null, claimTenantId, claimProjectIds, claimRoles,
                    claimPermissions, claimDepartmentScope, claimSalaryPerm, claimLocale);
        }

        UUID userId = user.getId();
        String locale = (claimLocale != null && !claimLocale.isBlank())
                ? claimLocale : user.getDefaultLocale();

        // 3) Pick the active tenant deterministically (see pickActiveMembership
        //    for the full precedence). A user with at least one ACTIVE membership
        //    ALWAYS gets a usable active tenant + its roles/permissions — never
        //    null — so a Super Admin who belongs to multiple company-clients does
        //    not lose all rights (the multi-membership bug).
        List<UserTenantMembershipJpaEntity> activeMemberships =
                memberships.findAllByUserId(userId).stream()
                        .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                        .toList();
        UserTenantMembershipJpaEntity activeMembership =
                pickActiveMembership(activeMemberships, claimTenantId, readActiveTenantHeader());

        // 4) Expand roles + permissions for the active (user, tenant). When the
        //    token already carried roles/permissions claims we keep them (the
        //    IdP is authoritative); otherwise expand from the DB.
        Set<String> resolvedRoles = claimRoles;
        Set<String> resolvedPermissions = claimPermissions;
        boolean salaryPerm = claimSalaryPerm;
        UUID tenantId = activeMembership != null ? activeMembership.getTenantId() : null;

        if (activeMembership != null && claimRoles.isEmpty() && claimPermissions.isEmpty()) {
            Authority authority = expandAuthority(activeMembership);
            resolvedRoles = authority.roleCodes();
            resolvedPermissions = authority.permissionCodes();
            // Salary gate is the membership flag unless the token explicitly
            // asserted it (claim wins when present and true).
            if (!claimSalaryPerm) {
                salaryPerm = authority.salaryPermission();
            }
        }

        // E4-S0: populate ABAC scope claim-first, then DB fallback (mirrors the
        // role/permission expansion above). projectIds come from
        // user_project_assignments; departmentScope is the user_department_scopes
        // roots EXPANDED to the department subtree. Fail-closed inside the
        // expander — an unscoped user gets empty sets and is unaffected.
        Set<UUID> resolvedProjectIds =
                scopeExpander.resolveProjectIds(userId, tenantId, claimProjectIds);
        Set<UUID> resolvedDepartmentScope =
                scopeExpander.resolveDepartmentScope(userId, tenantId, claimDepartmentScope);

        return new TenantContext(userId, tenantId, resolvedProjectIds, resolvedRoles,
                resolvedPermissions, resolvedDepartmentScope, salaryPerm, locale);
    }

    /**
     * Resolve the grading control-plane user for this principal. Order:
     * <ol>
     *   <li>by {@code sub} when it happens to be a grading {@code users.id};</li>
     *   <li>by {@code email} claim (case-insensitive) — the ZITADEL path;</li>
     *   <li>by {@code external_idp_subject == sub} — pre-linked accounts.</li>
     * </ol>
     */
    private UserJpaEntity resolveUser(Jwt jwt, UUID claimUserId) {
        if (claimUserId != null) {
            Optional<UserJpaEntity> byId = users.findById(claimUserId);
            if (byId.isPresent()) {
                return byId.get();
            }
        }
        String email = jwt.getClaimAsString(JwtClaimNames.EMAIL);
        if (email != null && !email.isBlank()) {
            Optional<UserJpaEntity> byEmail = users.findByEmailIgnoreCase(email.trim());
            if (byEmail.isPresent()) {
                return byEmail.get();
            }
        }
        String subject = jwt.getSubject();
        if (subject != null && !subject.isBlank()) {
            return users.findByExternalIdpSubject(subject).orElse(null);
        }
        return null;
    }

    /**
     * Select the active membership from the user's ACTIVE memberships, applying
     * a single deterministic precedence used CONSISTENTLY by both the
     * {@code TenantContext} path ({@code TenantContextFilter}) and the authorities
     * path ({@code GradingJwtAuthenticationConverter}) — both reach this method
     * through {@link #resolve(Jwt)}, so they always agree.
     *
     * <p>Precedence:
     * <ol>
     *   <li><b>{@code X-Active-Tenant-Id} header</b> — the SPA tenant switcher's
     *       declared active company. Honoured ONLY if the user has an ACTIVE
     *       membership in it.</li>
     *   <li><b>{@code active_tenant_id} claim</b> — honoured ONLY if the user has
     *       an ACTIVE membership in it.</li>
     *   <li><b>single ACTIVE membership</b> — the one membership (unambiguous
     *       default; no tenant to choose between).</li>
     *   <li><b>fail closed</b> — with MORE THAN ONE ACTIVE membership and neither
     *       a header nor a claim resolving to a member tenant, return
     *       {@code null} (NO active tenant). The caller then yields a
     *       {@code tenantId == null} context, downstream reads fail closed (the
     *       RLS GUC is left unset → zero rows; {@code requireActive()} rejects),
     *       and the SPA shows a "select a company-client" state.</li>
     * </ol>
     *
     * <p>SECURITY (BE-3, 2026-06-12): a multi-membership user must NEVER be
     * silently defaulted to some "earliest-created" tenant when the active
     * tenant is ambiguous — that leaked a default tenant's data (e.g. an
     * unattended inbox poll fired before the tenant switcher set the header).
     * Failing closed to no-active-tenant is the safe behaviour: zero rows, no
     * cross-tenant exposure. A header/claim pointing at a NON-member tenant is
     * still silently ignored (falls through to fail-closed) — no cross-tenant
     * escalation. The downstream {@code TenantContextFilter} membership
     * cross-check stays in place as defense in depth.
     */
    private UserTenantMembershipJpaEntity pickActiveMembership(
            List<UserTenantMembershipJpaEntity> active, UUID claimTenantId, UUID headerTenantId) {
        if (active.isEmpty()) {
            return null;
        }
        // 1) Header wins — but only for a tenant the user actually belongs to.
        UserTenantMembershipJpaEntity byHeader = findActiveByTenant(active, headerTenantId);
        if (byHeader != null) {
            return byHeader;
        }
        // 2) active_tenant_id claim — same membership cross-check.
        UserTenantMembershipJpaEntity byClaim = findActiveByTenant(active, claimTenantId);
        if (byClaim != null) {
            return byClaim;
        }
        // 3) Exactly one ACTIVE membership — unambiguous default.
        if (active.size() == 1) {
            return active.get(0);
        }
        // 4) Ambiguous: >1 ACTIVE membership and neither header nor claim
        //    resolved to a member tenant. FAIL CLOSED — no active tenant — rather
        //    than leaking a default tenant's data. The SPA must send a valid
        //    X-Active-Tenant-Id (tenant switcher) for reads to return rows.
        log.warn("Ambiguous active tenant: user has {} ACTIVE memberships but no "
                + "X-Active-Tenant-Id header / active_tenant_id claim resolved to a "
                + "member tenant — resolving to NO active tenant (fail closed).",
                active.size());
        return null;
    }

    /**
     * Return the ACTIVE membership matching {@code wantedTenantId}, or null if the
     * id is null or the user has no ACTIVE membership in that tenant. This is the
     * membership cross-check that prevents honouring a header/claim for a tenant
     * the user does not belong to.
     */
    private static UserTenantMembershipJpaEntity findActiveByTenant(
            List<UserTenantMembershipJpaEntity> active, UUID wantedTenantId) {
        if (wantedTenantId == null) {
            return null;
        }
        for (UserTenantMembershipJpaEntity m : active) {
            if (wantedTenantId.equals(m.getTenantId())) {
                return m;
            }
        }
        return null;
    }

    /**
     * Read the {@code X-Active-Tenant-Id} header from the current request via
     * {@link RequestContextHolder}. Both the security-filter (converter) path and
     * the {@code TenantContextFilter} path run on the request thread, so both see
     * the same header — keeping the two paths in agreement. When no request is
     * bound (e.g. async/background resolution), returns null and the next
     * precedence rule applies.
     */
    private static UUID readActiveTenantHeader() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                HttpServletRequest request = servletAttrs.getRequest();
                return parseUuid(request.getHeader(JwtClaimNames.ACTIVE_TENANT_HEADER));
            }
        } catch (Exception ex) {
            // Header resolution must never break authn — fall through to claim/default.
            log.debug("Could not read {} header from request context", JwtClaimNames.ACTIVE_TENANT_HEADER, ex);
        }
        return null;
    }

    /** Snapshot of the RBAC expanded for one membership. */
    private record Authority(Set<String> roleCodes, Set<String> permissionCodes,
                             boolean salaryPermission) {
    }

    /**
     * Expand role codes + permission codes for a membership. Identical query
     * path to {@link DevUserAuthorityResolver} so dev and prod resolve the same
     * authority set for the same DB state.
     */
    private Authority expandAuthority(UserTenantMembershipJpaEntity membership) {
        List<UUID> roleIds = userRoles.findAllByMembershipId(membership.getId()).stream()
                .map(UserRoleJpaEntity::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            return new Authority(Set.of(), Set.of(), membership.isSalaryDataPermission());
        }
        Set<String> roleCodes = roles.findAllById(roleIds).stream()
                .map(r -> r.getCode())
                .collect(Collectors.toUnmodifiableSet());
        List<String> permissionCodeList = rolePermissions.findPermissionCodesByRoleIds(roleIds);
        Set<String> permissionCodes = permissionCodeList.isEmpty()
                ? Set.of()
                : Set.copyOf(permissionCodeList);
        return new Authority(roleCodes, permissionCodes, membership.isSalaryDataPermission());
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Set<UUID> parseUuidSet(Object raw) {
        if (raw == null) return Set.of();
        if (raw instanceof Collection<?> col) {
            Set<UUID> out = new HashSet<>();
            for (Object o : col) {
                UUID u = parseUuid(Objects.toString(o, null));
                if (u != null) out.add(u);
            }
            return Set.copyOf(out);
        }
        return Set.of();
    }

    private static Set<String> parseStringSet(Object raw) {
        if (raw == null) return Set.of();
        if (raw instanceof Collection<?> col) {
            Set<String> out = new HashSet<>();
            for (Object o : col) {
                if (o != null) out.add(o.toString());
            }
            return Set.copyOf(out);
        }
        if (raw instanceof String s && !s.isBlank()) {
            return Set.of(List.of(s.split(",")).stream().map(String::trim).toArray(String[]::new));
        }
        return Set.of();
    }
}
