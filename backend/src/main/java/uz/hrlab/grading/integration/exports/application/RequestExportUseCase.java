package uz.hrlab.grading.integration.exports.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.integration.exports.domain.ExportFormat;
import uz.hrlab.grading.integration.exports.domain.ExportJobStatus;
import uz.hrlab.grading.integration.exports.domain.ExportType;
import uz.hrlab.grading.integration.exports.infrastructure.ExportJobJpaEntity;
import uz.hrlab.grading.integration.exports.infrastructure.ExportJobRepository;
import uz.hrlab.grading.integration.worker.ExportGenerationJob;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class RequestExportUseCase {

    private final ExportJobRepository jobs;
    private final ExportGenerationJob worker;
    private final AuditService audit;

    public RequestExportUseCase(ExportJobRepository jobs,
                                ExportGenerationJob worker,
                                AuditService audit) {
        this.jobs = jobs;
        this.worker = worker;
        this.audit = audit;
    }

    @Transactional
    public UUID request(ExportType type, ExportFormat format, UUID projectId, String filterParams) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (type == null || format == null || projectId == null) {
            throw new ValidationException("EXPORT_PARAMS_REQUIRED");
        }
        String requiredPermission = ExportTypePermissions.requiredPermission(type);
        if (!ctx.hasPermission(requiredPermission)) {
            throw new PermissionDeniedException();
        }

        UUID jobId = UUID.randomUUID();
        ExportJobJpaEntity job = new ExportJobJpaEntity(
                jobId, ctx.tenantId(), projectId, type, format,
                ExportJobStatus.REQUESTED, ctx.userId(), OffsetDateTime.now(),
                filterParams,
                ExportTypePermissions.isSalaryBearing(type),
                false);
        job.setTraceId(UUID.randomUUID().toString());
        jobs.save(job);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(projectId)
                .actorUserId(ctx.userId())
                .action(AuditAction.EXPORT_REQUESTED)
                .entityType("ExportJob")
                .entityId(jobId)
                .reason("type=" + type + " format=" + format
                        + " salary=" + job.isContainsSalaryData())
                .build());

        worker.generate(jobId, ctx.tenantId());
        return jobId;
    }
}
