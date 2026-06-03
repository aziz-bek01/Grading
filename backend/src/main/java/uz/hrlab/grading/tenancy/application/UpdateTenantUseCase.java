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
 * Use case for {@code PATCH /api/v1/admin/tenants/{tenantId}}.
 *
 * <p>Allowed mutations: {@code displayName}, {@code defaultLocale},
 * {@code status} (ACTIVE / SUSPENDED only — archive goes through
 * {@link ArchiveTenantUseCase}). All fields are optional; null = no change.
 *
 * <p>Records ONE {@link AuditAction#TENANT_UPDATED} row with a human-readable
 * {@code reason} listing the keys that changed (no value dumps — values may
 * carry PII).
 */
@Service
public class UpdateTenantUseCase {

    private final TenantManagementPolicy policy;
    private final TenantRepository tenantRepository;
    private final GetTenantDetailsQuery detailsQuery;
    private final AuditService audit;

    public UpdateTenantUseCase(TenantManagementPolicy policy,
                               TenantRepository tenantRepository,
                               GetTenantDetailsQuery detailsQuery,
                               AuditService audit) {
        this.policy = policy;
        this.tenantRepository = tenantRepository;
        this.detailsQuery = detailsQuery;
        this.audit = audit;
    }

    @Transactional
    public TenantDetailResponse patch(UUID tenantId, String displayName,
                                      String defaultLocale, String status) {
        TenantContext ctx = TenantContextHolder.requireActive();
        policy.requireCanManageTenant(ctx, tenantId);

        TenantJpaEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(TenantAccessDeniedException::new);

        boolean changed = false;
        StringBuilder reason = new StringBuilder();

        if (displayName != null) {
            String trimmed = displayName.trim();
            if (trimmed.isEmpty()) {
                throw new ValidationException("TENANT_PATCH_BLANK_NAME",
                        "displayName cannot be blank when provided");
            }
            if (!trimmed.equals(tenant.getDisplayName())) {
                tenant.setDisplayName(trimmed);
                reason.append("displayName ");
                changed = true;
            }
        }

        if (defaultLocale != null && !defaultLocale.equals(tenant.getDefaultLocale())) {
            // Pattern is enforced by UpdateTenantRequest @Pattern; defense-in-depth
            // here in case the use case is invoked from a non-HTTP entry point.
            if (!java.util.Set.of("ru-RU", "uz-Cyrl-UZ", "uz-Latn-UZ", "en-US")
                    .contains(defaultLocale)) {
                throw new ValidationException("TENANT_PATCH_BAD_LOCALE",
                        "defaultLocale must be one of ru-RU|uz-Cyrl-UZ|uz-Latn-UZ|en-US");
            }
            // setDefaultLocale not yet exposed on TenantJpaEntity — see TenantJpaEntity setter list.
            // We extend via reflection-free path: re-persist with a new value through entity setter.
            tenant.setDefaultLocale(defaultLocale);
            reason.append("defaultLocale ");
            changed = true;
        }

        if (status != null) {
            TenantStatus target;
            try {
                target = TenantStatus.valueOf(status);
            } catch (IllegalArgumentException ex) {
                throw new ValidationException("TENANT_PATCH_BAD_STATUS",
                        "status must be ACTIVE or SUSPENDED");
            }
            if (target != TenantStatus.ACTIVE && target != TenantStatus.SUSPENDED) {
                throw new ValidationException("TENANT_PATCH_BAD_STATUS",
                        "status must be ACTIVE or SUSPENDED — use POST /archive for ARCHIVED");
            }
            if (target != tenant.getStatus()) {
                tenant.setStatus(target);
                reason.append("status=").append(target).append(' ');
                changed = true;
            }
        }

        if (changed) {
            tenantRepository.save(tenant);
            audit.record(AuditEvent.builder()
                    .tenantId(tenantId)
                    .actorUserId(ctx.userId())
                    .action(AuditAction.TENANT_UPDATED)
                    .entityType("Tenant")
                    .entityId(tenantId)
                    .reason(reason.toString().trim())
                    .build());
        }
        return detailsQuery.byId(tenantId);
    }
}
