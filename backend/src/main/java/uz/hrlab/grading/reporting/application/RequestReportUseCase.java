package uz.hrlab.grading.reporting.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.reporting.application.template.ReportTitleResolver;
import uz.hrlab.grading.reporting.domain.ReportFormat;
import uz.hrlab.grading.reporting.domain.ReportStatus;
import uz.hrlab.grading.reporting.domain.ReportType;
import uz.hrlab.grading.reporting.infrastructure.ReportGenerationJob;
import uz.hrlab.grading.reporting.infrastructure.ReportJpaEntity;
import uz.hrlab.grading.reporting.infrastructure.ReportRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Creates a {@code Report} row with status=REQUESTED and dispatches the
 * async generation job. Re-checks {@code REPORT_CREATE} on top of the
 * controller-level {@code @PreAuthorize} (defence-in-depth, see Phase 4
 * remediation perm re-check pattern).
 */
@Service
public class RequestReportUseCase {

    private final ReportRepository reports;
    private final ReportGenerationJob worker;
    private final AuditService audit;

    public RequestReportUseCase(ReportRepository reports,
                                ReportGenerationJob worker,
                                AuditService audit) {
        this.reports = reports;
        this.worker = worker;
        this.audit = audit;
    }

    @Transactional
    public UUID request(ReportType type, ReportFormat format, UUID projectId, String filterParams) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (type == null || format == null || projectId == null) {
            throw new ValidationException("REPORT_PARAMS_REQUIRED");
        }
        if (!ctx.hasPermission(PermissionCodes.REPORT_CREATE)) {
            throw new PermissionDeniedException();
        }

        UUID id = UUID.randomUUID();
        String title = ReportTitleResolver.resolve(type, ctx.locale());
        ReportJpaEntity report = new ReportJpaEntity(
                id, ctx.tenantId(), projectId, type, format,
                ReportStatus.REQUESTED, title,
                ctx.userId(), OffsetDateTime.now(),
                filterParams, ctx.locale(),
                false /* salary-bearing flag — false in MVP 2 */,
                false /* contains_personal_data — set by template if applicable */);
        report.setTraceId(UUID.randomUUID().toString());
        reports.save(report);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(projectId)
                .actorUserId(ctx.userId())
                .action(AuditAction.REPORT_REQUESTED)
                .entityType("Report")
                .entityId(id)
                .reason("type=" + type + " format=" + format)
                .build());

        worker.generate(id, ctx.tenantId());
        return id;
    }
}
