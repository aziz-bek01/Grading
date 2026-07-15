package uz.hrlab.grading.tenancy.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.SuperAdminRoleAuditor;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.domain.RoleScope;
import uz.hrlab.grading.access.infrastructure.RoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.RoleRepository;
import uz.hrlab.grading.access.infrastructure.UserRoleJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRoleRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.tenancy.domain.IsolationMode;
import uz.hrlab.grading.tenancy.domain.Tenant;
import uz.hrlab.grading.tenancy.domain.TenantStatus;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyRepository;
import uz.hrlab.grading.tenancy.infrastructure.TenancyProperties;
import uz.hrlab.grading.tenancy.infrastructure.TenantJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.util.List;
import java.util.UUID;

/**
 * Provisions a new tenant + its single client company row.
 *
 * <p>MVP 1 scope: writes the control-plane rows only. Full provisioning
 * saga (schema creation, baseline migrations, RLS, methodology cloning) is
 * tracked under database-architect / devops-sre — see database-blueprint §10.
 *
 * <h3>Shared-mode immediate usability (BE-TI-2026-06)</h3>
 * In SHARED tenancy mode there is no per-tenant schema/database to spin up, so
 * the tenant is usable the moment its {@code public.*} rows exist. We therefore
 * short-circuit the provisioning saga in shared mode by:
 * <ol>
 *   <li>creating the tenant with status {@link TenantStatus#ACTIVE} (instead of
 *       {@code PROVISIONING}); and</li>
 *   <li>auto-membering the CREATING HRLab user into the new tenant with the same
 *       HRLab <em>platform</em> roles they currently hold, so
 *       {@code JwtTenantContextResolver} can pick the new tenant as active and
 *       {@code GET /api/v1/users/me} immediately lists it in {@code tenants[]}.</li>
 * </ol>
 * DATABASE / SCHEMA isolation modes are untouched — they stay on the
 * {@code PROVISIONING} path until the schema/database saga marks them ACTIVE.
 * Only the creator is auto-membered; tenant isolation (RLS + app-level
 * predicates) is not weakened.
 */
@Service
public class CreateTenantUseCase {

    private final TenantRepository tenantRepository;
    private final ClientCompanyRepository clientCompanyRepository;
    private final UserTenantMembershipRepository membershipRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final TenancyProperties tenancyProperties;
    private final AuditService auditService;
    private final SuperAdminRoleAuditor superAdminAuditor;

    public CreateTenantUseCase(TenantRepository tenantRepository,
                               ClientCompanyRepository clientCompanyRepository,
                               UserTenantMembershipRepository membershipRepository,
                               UserRoleRepository userRoleRepository,
                               RoleRepository roleRepository,
                               TenancyProperties tenancyProperties,
                               AuditService auditService,
                               SuperAdminRoleAuditor superAdminAuditor) {
        this.tenantRepository = tenantRepository;
        this.clientCompanyRepository = clientCompanyRepository;
        this.membershipRepository = membershipRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.tenancyProperties = tenancyProperties;
        this.auditService = auditService;
        this.superAdminAuditor = superAdminAuditor;
    }

    @Transactional
    public Tenant create(CreateTenantCommand cmd) {
        if (!Tenant.SLUG_PATTERN.matcher(cmd.slug()).matches()) {
            throw new ValidationException("Slug does not match required pattern");
        }
        if (tenantRepository.existsBySlug(cmd.slug())) {
            throw new ValidationException("TENANT_SLUG_TAKEN", "Tenant slug already taken");
        }

        boolean sharedMode = tenancyProperties.getMode() == TenancyProperties.Mode.SHARED;

        UUID tenantId = UUID.randomUUID();
        String schemaName = cmd.isolationMode() == IsolationMode.SCHEMA
                ? Tenant.schemaNameFor(cmd.slug())
                : null;
        String databaseName = cmd.isolationMode() == IsolationMode.DATABASE
                ? "grading_tenant_" + cmd.slug()
                : null;

        // Shared mode has no schema/database saga, so the tenant is usable the
        // instant its rows exist — mark it ACTIVE. Other isolation modes stay
        // PROVISIONING until their schema/database saga completes.
        TenantStatus initialStatus = sharedMode ? TenantStatus.ACTIVE : TenantStatus.PROVISIONING;

        TenantJpaEntity tenant = new TenantJpaEntity(
                tenantId,
                cmd.slug(),
                cmd.displayName(),
                cmd.isolationMode(),
                schemaName,
                databaseName,
                initialStatus,
                cmd.defaultLocale());
        tenantRepository.save(tenant);

        ClientCompanyJpaEntity company = new ClientCompanyJpaEntity(
                UUID.randomUUID(), tenantId,
                cmd.companyLegalName(), cmd.companyBrandName(), cmd.companyIndustry(),
                null, null);
        clientCompanyRepository.save(company);

        auditService.record(AuditEvent.builder()
                .tenantId(tenantId)
                .action(AuditAction.TENANT_CREATED)
                .entityType("Tenant")
                .entityId(tenantId)
                .build());

        // In shared mode, immediately grant the creator access so they can switch
        // into the new tenant from the tenant switcher without a second admin step.
        if (sharedMode) {
            autoMemberCreator(tenantId);
        }

        return tenant.toDomain();
    }

    /**
     * Auto-provision an ACTIVE membership + mirrored HRLab platform roles for the
     * authenticated creator in the freshly-created tenant. Idempotent: re-running
     * (or a creator who somehow already has a membership) does not duplicate the
     * membership or its role rows.
     *
     * <p>salary_data_permission defaults to {@code false} — the salary gate is a
     * separate, deliberate grant (security-blueprint §5.1).
     *
     * <p>Only the creator's PLATFORM-scoped (HRLab) roles are mirrored. Cloning
     * tenant-scoped roles here would be meaningless — they belong to whatever
     * tenant the creator was acting in, not the new one.
     */
    private void autoMemberCreator(UUID tenantId) {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null || ctx.userId() == null) {
            // Defensive: TENANT_CREATE is only reachable by an authenticated
            // principal, but never NPE if the context is somehow absent.
            return;
        }
        UUID creatorId = ctx.userId();

        UserTenantMembershipJpaEntity membership = membershipRepository
                .findByUserIdAndTenantId(creatorId, tenantId)
                .orElse(null);
        if (membership == null) {
            membership = new UserTenantMembershipJpaEntity(UUID.randomUUID(),
                    creatorId, tenantId, MembershipStatus.ACTIVE, false);
            membershipRepository.save(membership);
            auditService.record(AuditEvent.builder()
                    .tenantId(tenantId)
                    .actorUserId(creatorId)
                    .action(AuditAction.USER_MEMBERSHIP_ADDED)
                    .entityType("UserTenantMembership")
                    .entityId(membership.getId())
                    .reason("auto-membership on tenant creation; userId=" + creatorId)
                    .build());
        } else if (membership.getStatus() == MembershipStatus.REVOKED) {
            membership.setStatus(MembershipStatus.ACTIVE);
            membershipRepository.save(membership);
        }

        // Mirror the creator's HRLab platform roles into the new tenant so the
        // resolver expands the same authorities (e.g. HRLAB_SUPER_ADMIN) there.
        List<RoleJpaEntity> platformRoles = resolveCreatorPlatformRoles(ctx);
        for (RoleJpaEntity role : platformRoles) {
            if (!userRoleRepository.existsByMembershipIdAndRoleId(membership.getId(), role.getId())) {
                UserRoleJpaEntity userRole = new UserRoleJpaEntity(
                        UUID.randomUUID(), membership.getId(), role.getId());
                userRoleRepository.save(userRole);
                auditService.record(AuditEvent.builder()
                        .tenantId(tenantId)
                        .actorUserId(creatorId)
                        .action(AuditAction.USER_ROLE_ASSIGNED)
                        .entityType("UserRole")
                        .entityId(userRole.getId())
                        .reason("auto-role on tenant creation; roleCode=" + role.getCode()
                                + " userId=" + creatorId)
                        .build());
                // Blast-radius control: the creator's platform roles are mirrored
                // into the new tenant — this materialises a fresh HRLAB_SUPER_ADMIN
                // user_role row when the creator is a super admin (self-mirror,
                // actor == target), so it is audited + alerted like any other
                // super-admin grant. NO-OP for any other role.
                superAdminAuditor.onRoleGranted(creatorId, tenantId, creatorId,
                        role.getCode(), userRole.getId(), "tenant-create-self-mirror");
            }
        }
    }

    /**
     * Resolve the PLATFORM-scoped roles the creator currently holds, from the
     * role codes in their security context. Unknown / tenant-scoped roles are
     * skipped, so a creator acting purely with tenant roles auto-members with no
     * roles (membership alone still lets them switch in and re-self-grant).
     */
    private List<RoleJpaEntity> resolveCreatorPlatformRoles(TenantContext ctx) {
        if (ctx.roles() == null || ctx.roles().isEmpty()) {
            return List.of();
        }
        return ctx.roles().stream()
                .map(roleRepository::findByCode)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(r -> r.getScope() == RoleScope.PLATFORM)
                .toList();
    }
}
