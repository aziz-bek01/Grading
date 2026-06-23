package uz.hrlab.grading.reporting.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.reporting.application.template.EvaluationReportFilter;
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
import java.util.List;
import java.util.UUID;

/**
 * Creates a {@code Report} row with status=REQUESTED and dispatches the
 * async generation job. Re-checks {@code REPORT_CREATE} on top of the
 * controller-level {@code @PreAuthorize} (defence-in-depth, see Phase 4
 * remediation perm re-check pattern).
 */
@Service
public class RequestReportUseCase {

    /**
     * In-flight statuses guarded by the {@code uq_reports_inflight} partial unique
     * index (028-create-reports.yaml): only one report per
     * (tenant, requestor, type, project) may sit in any of these at a time.
     */
    private static final List<ReportStatus> IN_FLIGHT_STATUSES =
            List.of(ReportStatus.REQUESTED, ReportStatus.QUEUED, ReportStatus.GENERATING);

    private final ReportRepository reports;
    private final ReportGenerationJob worker;
    private final AuditService audit;
    private final EvaluationReportFilterValidator filterValidator;
    private final CancelReportUseCase cancelUseCase;

    @PersistenceContext
    private EntityManager em;

    public RequestReportUseCase(ReportRepository reports,
                                ReportGenerationJob worker,
                                AuditService audit,
                                EvaluationReportFilterValidator filterValidator,
                                CancelReportUseCase cancelUseCase) {
        this.reports = reports;
        this.worker = worker;
        this.audit = audit;
        this.filterValidator = filterValidator;
        this.cancelUseCase = cancelUseCase;
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

        // Parse + validate the structured filter at REQUEST time (fail fast).
        // Throws REPORT_FILTER_MALFORMED / REPORT_FILTER_INVALID_DATE_RANGE /
        // REPORT_FILTER_INVALID_METHODOLOGY before the row is ever persisted.
        EvaluationReportFilter filter =
                filterValidator.validate(type, ctx.tenantId(), filterParams);

        // Self-heal the uq_reports_inflight partial unique index. An orphaned
        // in-flight report — e.g. one left stuck in REQUESTED/GENERATING by an
        // earlier failure — for the same (tenant, requestor, type, project) would
        // otherwise make EVERY new request fail with a unique-constraint violation
        // (surfaced to the user as "the request isn't even being created"). Supersede
        // it: cancel the stale row(s) and FLUSH so the UPDATE lands before the new
        // INSERT (Hibernate executes inserts before updates within a transaction,
        // which would otherwise re-trigger the very violation we are avoiding).
        List<ReportJpaEntity> inFlight =
                reports.findAllByTenantIdAndRequestedByAndReportTypeAndProjectIdAndStatusIn(
                        ctx.tenantId(), ctx.userId(), type, projectId, IN_FLIGHT_STATUSES);
        if (!inFlight.isEmpty()) {
            for (ReportJpaEntity stale : inFlight) {
                cancelUseCase.cancel(stale.getId());
            }
            em.flush();
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
                .reason("type=" + type + " format=" + format + filterCardinality(filter))
                .build());

        // PERF/CORRECTNESS (P1) — dispatch the @Async worker only AFTER commit so
        // the Report row is visible when the worker (its own tx) loads it; inline
        // dispatch raced the commit and could silently drop the job. On rollback
        // no dispatch occurs.
        UUID tenantId = ctx.tenantId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    worker.generate(id, tenantId);
                }
            });
        } else {
            worker.generate(id, tenantId);
        }
        return id;
    }

    /**
     * Append filter CARDINALITY COUNTS only (decision D5 / NFR-5) to the audit
     * reason — never raw ids or names (no PII in the forensic reason string).
     * Empty filter ⇒ empty suffix (no regression to the legacy reason shape).
     */
    private static String filterCardinality(EvaluationReportFilter filter) {
        if (filter == null || filter.isEmpty()) {
            return "";
        }
        return " filters={methodologyVersions:" + filter.methodologyVersionIds().size()
                + ",evaluators:" + filter.evaluatorUserIds().size()
                + ",dateRange:" + (filter.dateFrom() != null || filter.dateTo() != null) + "}";
    }
}
