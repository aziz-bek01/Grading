package uz.hrlab.grading.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uz.hrlab.grading.access.application.MembershipAuthorityResolver;
import uz.hrlab.grading.access.application.PlatformSuperAdminChecker;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.access.application.UserScopeExpander;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.infrastructure.UserJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.domain.TenantStatus;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    private final MembershipAuthorityResolver authorityResolver;
    private final UserScopeExpander scopeExpander;
    private final PlatformSuperAdminChecker superAdminChecker;
    private final TenantRepository tenants;

    public JwtTenantContextResolver(UserRepository users,
                                    UserTenantMembershipRepository memberships,
                                    MembershipAuthorityResolver authorityResolver,
                                    UserScopeExpander scopeExpander,
                                    PlatformSuperAdminChecker superAdminChecker,
                                    TenantRepository tenants) {
        this.users = users;
        this.memberships = memberships;
        this.authorityResolver = authorityResolver;
        this.scopeExpander = scopeExpander;
        this.superAdminChecker = superAdminChecker;
        this.tenants = tenants;
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
        UUID headerTenantId = readActiveTenantHeader();
        UserTenantMembershipJpaEntity activeMembership =
                pickActiveMembership(activeMemberships, claimTenantId, headerTenantId);

        // ---- Fix A: platform Super-Admin cross-tenant activation ----------------
        // This is the ONLY path by which the resolved tenantId may be a tenant the
        // user is NOT a member of. It is gated STRICTLY on ALL of:
        //   (1) an EXPLICIT requested tenant — the X-Active-Tenant-Id header, else
        //       the active_tenant_id claim — that is NOT one of the user's ACTIVE
        //       memberships (a member tenant is already handled by
        //       pickActiveMembership, unchanged);
        //   (2) the DB-derived platform-super-admin predicate (never a JWT/claim
        //       role, never the target tenant, only HRLAB_SUPER_ADMIN);
        //   (3) the target tenant being ACTIVE (never PROVISIONING/SUSPENDED/
        //       ARCHIVED).
        // Every other role fails (2) → falls through to the unchanged member-only
        // resolution. It never auto-picks a tenant (requestedTenant must be
        // present). Authority is expanded from the super admin's OWN platform
        // HRLAB_SUPER_ADMIN membership — NEVER from a target-tenant membership.
        // Checked BEFORE using activeMembership so a SINGLE-membership super admin
        // can still switch AWAY into another company (the "single membership
        // default" must not trap them in their home tenant).
        UUID requestedTenant = headerTenantId != null ? headerTenantId : claimTenantId;
        if (requestedTenant != null
                && findActiveByTenant(activeMemberships, requestedTenant) == null
                && superAdminChecker.isPlatformSuperAdmin(userId)
                && isActiveTenant(requestedTenant)) {
            log.info("Fix A: platform super-admin cross-tenant activation userId={} "
                            + "targetTenantId={} (no membership; tenant ACTIVE) — synthesizing "
                            + "context from the caller's platform HRLAB_SUPER_ADMIN role.",
                    userId, requestedTenant);
            return synthesizeSuperAdminContext(userId, locale, requestedTenant, activeMemberships);
        }

        // 4) Expand roles + permissions for the active (user, tenant). When the
        //    token already carried roles/permissions claims we keep them (the
        //    IdP is authoritative); otherwise expand from the DB.
        Set<String> resolvedRoles = claimRoles;
        Set<String> resolvedPermissions = claimPermissions;
        boolean salaryPerm = claimSalaryPerm;
        UUID tenantId = activeMembership != null ? activeMembership.getTenantId() : null;

        // Latent-bug fix (BE-3-FIX): expand whenever the token carried NO
        // permissions claim — gate on claimPermissions ONLY, not also on
        // claimRoles. The previous guard (claimRoles.isEmpty() &&
        // claimPermissions.isEmpty()) silently suppressed DB permission
        // expansion if an IdP ever emitted a flat `roles` claim WITHOUT a
        // `permissions` claim, leaving the principal with zero permissions.
        // Permissions are what gate the app, so they drive the decision.
        if (activeMembership != null && claimPermissions.isEmpty()) {
            MembershipAuthorityResolver.Authority authority =
                    authorityResolver.expand(activeMembership);
            // Keep an explicit roles claim if present (IdP authoritative);
            // otherwise take the DB-expanded role codes.
            if (claimRoles.isEmpty()) {
                resolvedRoles = authority.roleCodes();
            }
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
     * True iff {@code tenantId} exists and is {@link TenantStatus#ACTIVE}. This is
     * a control-plane read ({@code public.tenants} carries no {@code tenant_id} and
     * is not RLS-scoped). The ACTIVE gate is what confines the Fix A carve-out to
     * live tenants only — a super admin can NEVER activate a PROVISIONING /
     * SUSPENDED / ARCHIVED tenant they lack a membership in.
     */
    private boolean isActiveTenant(UUID tenantId) {
        if (tenantId == null) {
            return false;
        }
        return tenants.findById(tenantId)
                .map(t -> t.getStatus() == TenantStatus.ACTIVE)
                .orElse(false);
    }

    /**
     * Build the synthesized cross-tenant context for a platform super admin acting
     * in {@code targetTenantId} (Fix A). The {@code tenantId} IS the target, so the
     * RLS GUC ({@code app.tenant_id}) binds to it and every read stays scoped to
     * that tenant. Roles/permissions/salary are expanded from the super admin's OWN
     * platform HRLAB_SUPER_ADMIN membership — NEVER from a target-tenant membership
     * (there is none). ABAC scope is intentionally EMPTY: the super admin holds no
     * project/department assignments in the target tenant, and their reach there is
     * ROLE-based (HRLAB_SUPER_ADMIN tenant-wide bypass in
     * {@code DepartmentScopePolicy}), not scope-based.
     *
     * <p>F-3 (hardening): salaryPermission is FORCED to {@code false} for a
     * cross-tenant synthesized context — the super admin may see grades/structure
     * across tenants but NOT salary in a tenant they are not a member of, honouring
     * the product's strict salary-separation rule. Their salary access in tenants
     * they ARE a member of is unaffected (that path never reaches this method — it
     * carries the real membership's salary flag). This {@code false} is the
     * fail-safe default pending a DPO ruling on cross-tenant salary visibility.
     */
    private TenantContext synthesizeSuperAdminContext(
            UUID userId, String locale, UUID targetTenantId,
            List<UserTenantMembershipJpaEntity> activeMemberships) {
        MembershipAuthorityResolver.Authority authority =
                expandPlatformSuperAdminAuthority(activeMemberships);
        return new TenantContext(userId, targetTenantId, Set.of(),
                authority.roleCodes(), authority.permissionCodes(), Set.of(),
                false, locale);
    }

    /**
     * Deterministically pick the caller's ACTIVE membership that actually carries
     * HRLAB_SUPER_ADMIN and expand THAT ONE membership's authority — never unions
     * permissions across tenants (the invariant {@link MembershipAuthorityResolver}
     * documents). The predicate already proved such a membership exists; the
     * defensive {@code empty()} fallback keeps this fail-closed if it somehow does
     * not (yields a permission-empty context, not another tenant's rights).
     */
    private MembershipAuthorityResolver.Authority expandPlatformSuperAdminAuthority(
            List<UserTenantMembershipJpaEntity> activeMemberships) {
        return activeMemberships.stream()
                .sorted(Comparator
                        .comparing(UserTenantMembershipJpaEntity::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(UserTenantMembershipJpaEntity::getTenantId))
                .map(authorityResolver::expand)
                .filter(a -> a.roleCodes().contains(RoleCodes.HRLAB_SUPER_ADMIN))
                .findFirst()
                .orElse(MembershipAuthorityResolver.Authority.empty());
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
     * Read the {@code X-Active-Tenant-Id} header for the current request.
     *
     * <h3>Why the ThreadLocal is read FIRST (BE-AUTH-MT-001)</h3>
     * This method is invoked from {@link GradingJwtAuthenticationConverter} INSIDE
     * the OAuth2 resource-server authentication filter, which runs BEFORE Spring's
     * {@code DispatcherServlet} binds {@link RequestContextHolder}. At that point
     * {@code RequestContextHolder.getRequestAttributes()} is intermittently
     * {@code null} (it is not bound, or is bound non-inheritably and cleared), so
     * the header was unreadable — a multi-membership Super Admin then fell through
     * to AMBIGUOUS → no active tenant → 404 on tenant-scoped by-id reads.
     *
     * <p>{@link ActiveTenantHeaderFilter} runs at HIGHEST precedence (before the
     * security chain) and binds the parsed header on {@link ActiveTenantHeaderHolder}
     * deterministically, so we read THAT first. We fall back to
     * {@link RequestContextHolder} only for code paths where the early filter did
     * not run (e.g. a bare unit test or async re-resolution), preserving the
     * previous behaviour as a safety net. When neither source has a value, returns
     * {@code null} and the next precedence rule (claim / single membership /
     * fail-closed) applies — the fail-closed safety is unchanged.
     */
    private static UUID readActiveTenantHeader() {
        // 1) Reliable per-request ThreadLocal, set by ActiveTenantHeaderFilter
        //    before the security chain runs.
        UUID fromHolder = ActiveTenantHeaderHolder.get();
        if (fromHolder != null) {
            return fromHolder;
        }
        // 2) Fallback: RequestContextHolder, for paths where the early filter did
        //    not run. May be null at JWT-auth time — that is exactly why (1) exists.
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
