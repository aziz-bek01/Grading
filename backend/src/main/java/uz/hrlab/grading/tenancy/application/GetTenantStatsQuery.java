package uz.hrlab.grading.tenancy.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogJpaEntity;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogRepository;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.api.TenantStatsResponse;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Query handler for {@code GET /api/v1/admin/tenants/{tenantId}/stats}.
 *
 * <p>Computes lightweight tenant KPIs:
 * <ul>
 *   <li>{@code projectCount} — {@code count(projects)} for the tenant
 *       (tenant-aware via {@code ProjectRepository.countByTenantId}).</li>
 *   <li>{@code userCount} — distinct ACTIVE memberships for the tenant
 *       ({@code user_tenant_memberships.countByTenantId}).</li>
 *   <li>{@code lastActivityAt} — newest {@code system_audit_log.created_at}
 *       for the tenant. Null when the tenant has no audit rows yet.</li>
 * </ul>
 *
 * <p>All three queries are O(1) on indexed columns
 * (architecture §17 perf budget: less than 50ms p95).
 */
@Service
public class GetTenantStatsQuery {

    private final TenantManagementPolicy policy;
    private final ProjectRepository projectRepository;
    private final UserTenantMembershipRepository membershipRepository;
    private final SystemAuditLogRepository auditRepository;

    public GetTenantStatsQuery(TenantManagementPolicy policy,
                               ProjectRepository projectRepository,
                               UserTenantMembershipRepository membershipRepository,
                               SystemAuditLogRepository auditRepository) {
        this.policy = policy;
        this.projectRepository = projectRepository;
        this.membershipRepository = membershipRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional(readOnly = true)
    public TenantStatsResponse byId(UUID tenantId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        policy.requireCanViewTenant(ctx, tenantId);

        long projectCount = projectRepository.countByTenantId(tenantId);
        long userCount = membershipRepository.countByTenantId(tenantId);

        // Cheapest "last activity" probe: take top-1 of the audit feed.
        // Audit table is RANGE-partitioned on created_at so this is a single
        // partition scan via the (tenant_id, created_at DESC) index.
        // PERF — bounded finder (limit 1); the unbounded variant materialised the
        // whole tenant feed just to read the head.
        OffsetDateTime lastActivity = auditRepository
                .findFirstByTenantIdOrderByCreatedAtDesc(tenantId)
                .map(SystemAuditLogJpaEntity::getCreatedAt)
                .orElse(null);

        return new TenantStatsResponse(tenantId, projectCount, userCount, lastActivity);
    }
}
