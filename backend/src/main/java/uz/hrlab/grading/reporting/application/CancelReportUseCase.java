package uz.hrlab.grading.reporting.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.reporting.api.ReportResponse;
import uz.hrlab.grading.reporting.domain.ReportStatus;
import uz.hrlab.grading.reporting.domain.ReportStatusTransitionPolicy;
import uz.hrlab.grading.reporting.infrastructure.ReportJpaEntity;
import uz.hrlab.grading.reporting.infrastructure.ReportRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

@Service
public class CancelReportUseCase {

    private final ReportRepository reports;
    private final AuditService audit;

    public CancelReportUseCase(ReportRepository reports, AuditService audit) {
        this.reports = reports;
        this.audit = audit;
    }

    // BE-035 — returns the wire DTO (mapped in-tx). The single internal caller
    // (RequestReportUseCase stale-cleanup) discards the return value.
    @Transactional
    public ReportResponse cancel(UUID id) {
        TenantContext ctx = TenantContextHolder.requireActive();
        ReportJpaEntity report = reports.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        ReportStatusTransitionPolicy.assertAllowed(report.getStatus(), ReportStatus.CANCELLED);
        report.setStatus(ReportStatus.CANCELLED);
        audit.record(AuditEvent.builder(ctx)
                .projectId(report.getProjectId())
                .action(AuditAction.REPORT_CANCELLED)
                .entityType("Report")
                .entityId(report.getId())
                .build());
        return ReportResponse.from(reports.save(report));
    }
}
