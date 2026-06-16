package uz.hrlab.grading.analytics.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.analytics.api.PortfolioSummaryResponse;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogJpaEntity;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Query handler for {@code GET /api/v1/analytics/portfolio-summary} — the
 * dashboard portfolio widget.
 *
 * <p><b>Tenant scope.</b> The tenant is ALWAYS sourced from the active
 * {@link TenantContext} (server-derived from the JWT), never from a request
 * parameter — so a caller can only ever see their own tenant's counts. Every
 * count is a tenant-bound aggregate on an indexed column (O(1), reuses the same
 * {@code countByTenantId} predicates the admin tenant-stats endpoint uses).
 */
@Service
public class GetPortfolioSummaryQuery {

    private final ClientCompanyRepository clientCompanies;
    private final ProjectRepository projects;
    private final MethodologyRepository methodologies;
    private final SystemAuditLogRepository auditRepository;

    public GetPortfolioSummaryQuery(ClientCompanyRepository clientCompanies,
                                    ProjectRepository projects,
                                    MethodologyRepository methodologies,
                                    SystemAuditLogRepository auditRepository) {
        this.clientCompanies = clientCompanies;
        this.projects = projects;
        this.methodologies = methodologies;
        this.auditRepository = auditRepository;
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse summary() {
        TenantContext ctx = TenantContextHolder.requireActive();
        UUID tenantId = ctx.tenantId();

        long clientCompanyCount = clientCompanies.countByTenantId(tenantId);
        long projectCount = projects.countByTenantId(tenantId);
        long methodologyCount = methodologies.countByTenantId(tenantId);

        // Cheapest "last activity" probe: top-1 of the tenant audit feed
        // (single partition scan via the (tenant_id, created_at DESC) index).
        // PERF — bounded finder (limit 1); the unbounded variant materialised the
        // whole tenant feed just to read the head on every dashboard load.
        OffsetDateTime lastActivity = auditRepository
                .findFirstByTenantIdOrderByCreatedAtDesc(tenantId)
                .map(SystemAuditLogJpaEntity::getCreatedAt)
                .orElse(null);

        return new PortfolioSummaryResponse(
                tenantId, clientCompanyCount, projectCount, methodologyCount, lastActivity);
    }
}
