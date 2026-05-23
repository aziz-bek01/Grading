package uz.hrlab.grading.tenancy.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.tenancy.domain.IsolationMode;
import uz.hrlab.grading.tenancy.domain.Tenant;
import uz.hrlab.grading.tenancy.domain.TenantStatus;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.ClientCompanyRepository;
import uz.hrlab.grading.tenancy.infrastructure.TenantJpaEntity;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.util.UUID;

/**
 * Provisions a new tenant + its single client company row.
 *
 * <p>MVP 1 scope: writes the control-plane rows only. Full provisioning
 * saga (schema creation, baseline migrations, RLS, methodology cloning) is
 * tracked under database-architect / devops-sre — see database-blueprint §10.
 */
@Service
public class CreateTenantUseCase {

    private final TenantRepository tenantRepository;
    private final ClientCompanyRepository clientCompanyRepository;
    private final AuditService auditService;

    public CreateTenantUseCase(TenantRepository tenantRepository,
                               ClientCompanyRepository clientCompanyRepository,
                               AuditService auditService) {
        this.tenantRepository = tenantRepository;
        this.clientCompanyRepository = clientCompanyRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Tenant create(CreateTenantCommand cmd) {
        if (!Tenant.SLUG_PATTERN.matcher(cmd.slug()).matches()) {
            throw new ValidationException("Slug does not match required pattern");
        }
        if (tenantRepository.existsBySlug(cmd.slug())) {
            throw new ValidationException("TENANT_SLUG_TAKEN", "Tenant slug already taken");
        }

        UUID tenantId = UUID.randomUUID();
        String schemaName = cmd.isolationMode() == IsolationMode.SCHEMA
                ? Tenant.schemaNameFor(cmd.slug())
                : null;
        String databaseName = cmd.isolationMode() == IsolationMode.DATABASE
                ? "grading_tenant_" + cmd.slug()
                : null;

        TenantJpaEntity tenant = new TenantJpaEntity(
                tenantId,
                cmd.slug(),
                cmd.displayName(),
                cmd.isolationMode(),
                schemaName,
                databaseName,
                TenantStatus.PROVISIONING,
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

        return tenant.toDomain();
    }
}
