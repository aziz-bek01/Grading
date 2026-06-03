package uz.hrlab.grading.tenancy.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.tenancy.api.TenantDetailResponse;
import uz.hrlab.grading.tenancy.domain.TenantStatus;
import uz.hrlab.grading.tenancy.infrastructure.TenantJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.util.UUID;

/**
 * Use case for {@code POST /api/v1/admin/tenants/{tenantId}/archive}.
 *
 * <p>Soft-archive — flips {@code tenants.status = ARCHIVED}. The tenant's
 * data rows are NOT deleted (MVP 1 retention rule: data lives 7 years post
 * archive — database-blueprint §15.2). Full hard-purge is a separate
 * back-office job.
 *
 * <p>Idempotent: archiving an already-archived tenant is a no-op (no audit
 * row, returns the current detail).
 */
@Service
public class ArchiveTenantUseCase {

    private final TenantManagementPolicy policy;
    private final TenantRepository tenantRepository;
    private final GetTenantDetailsQuery detailsQuery;
    private final AuditService audit;

    public ArchiveTenantUseCase(TenantManagementPolicy policy,
                                TenantRepository tenantRepository,
                                GetTenantDetailsQuery detailsQuery,
                                AuditService audit) {
        this.policy = policy;
        this.tenantRepository = tenantRepository;
        this.detailsQuery = detailsQuery;
        this.audit = audit;
    }

    @Transactional
    public TenantDetailResponse archive(UUID tenantId, String reason) {
        TenantContext ctx = TenantContextHolder.requireActive();
        policy.requireCanManageTenant(ctx, tenantId);

        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException("TENANT_ARCHIVE_REASON_REQUIRED",
                    "Archive reason is required");
        }

        TenantJpaEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(TenantAccessDeniedException::new);

        if (tenant.getStatus() == TenantStatus.ARCHIVED) {
            return detailsQuery.byId(tenantId);
        }

        tenant.setStatus(TenantStatus.ARCHIVED);
        tenantRepository.save(tenant);

        audit.record(AuditEvent.builder()
                .tenantId(tenantId)
                .actorUserId(ctx.userId())
                .action(AuditAction.TENANT_ARCHIVED)
                .entityType("Tenant")
                .entityId(tenantId)
                .reason(reason.trim())
                .build());

        return detailsQuery.byId(tenantId);
    }
}
