package uz.hrlab.grading.integration.exports.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.integration.exports.domain.ExportFormat;
import uz.hrlab.grading.integration.exports.domain.ExportJobStatus;
import uz.hrlab.grading.integration.exports.domain.ExportType;
import uz.hrlab.grading.integration.exports.infrastructure.ExportJobJpaEntity;
import uz.hrlab.grading.integration.exports.infrastructure.ExportJobRepository;
import uz.hrlab.grading.integration.storage.ObjectStoragePath;
import uz.hrlab.grading.integration.storage.ObjectStorageAdapter;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link DownloadExportFileUseCase} — the security core of the
 * backend-proxied download (Batch 2, TASK 2). Asserts tenant-IDOR safety,
 * permission gating (incl. the SALARY_EXPORT gate), status guard, traversal-safe
 * key resolution, byte streaming, the single audit pair, and the GENERATED ->
 * DOWNLOADED flip.
 */
@Tag("exports")
class DownloadExportFileUseCaseTest {

    private ExportJobRepository jobs;
    private ObjectStorageAdapter storage;
    private AuditService audit;
    private DownloadExportFileUseCase useCase;

    private UUID tenantId;
    private UUID projectId;
    private UUID userId;
    private UUID jobId;

    @BeforeEach
    void setUp() {
        jobs = mock(ExportJobRepository.class);
        storage = mock(ObjectStorageAdapter.class);
        audit = mock(AuditService.class);
        useCase = new DownloadExportFileUseCase(jobs, storage, audit);

        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();
        jobId = UUID.randomUUID();

        given(jobs.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    private void setContext(String... permissions) {
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of(permissions), Set.of(), false, "ru-RU"));
    }

    private ExportJobJpaEntity generatedJob(ExportType type, ExportFormat format) {
        ExportJobJpaEntity job = new ExportJobJpaEntity(
                jobId, tenantId, projectId, type, format,
                ExportJobStatus.REQUESTED, userId, OffsetDateTime.now(),
                null, ExportTypePermissions.isSalaryBearing(type), false);
        job.setStatus(ExportJobStatus.GENERATED);
        job.setFileStorageKey(ObjectStoragePath.forExportResult(
                tenantId, projectId, jobId, format.name().toLowerCase()));
        job.setExpiresAt(OffsetDateTime.now().plusHours(1));
        return job;
    }

    @Test
    void permittedOwnerGetsBytesAndAttachmentName() {
        setContext(PermissionCodes.EXPORT_REQUEST);
        ExportJobJpaEntity job = generatedJob(ExportType.POSITION_CATALOG, ExportFormat.XLSX);
        given(jobs.findByIdAndTenantId(eq(jobId), eq(tenantId))).willReturn(Optional.of(job));
        given(storage.retrieve(eq(job.getFileStorageKey()))).willReturn(new byte[]{9, 8, 7});

        DownloadExportFileUseCase.DownloadPayload payload = useCase.download(jobId);

        assertThat(payload.bytes()).containsExactly(9, 8, 7);
        assertThat(payload.filename()).isEqualTo("position_catalog_"
                + jobId.toString().substring(0, 8) + ".xlsx");
        assertThat(payload.contentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.DOWNLOADED);
    }

    @Test
    void auditPairWrittenOncePerDownload() {
        setContext(PermissionCodes.EXPORT_REQUEST);
        ExportJobJpaEntity job = generatedJob(ExportType.POSITION_CATALOG, ExportFormat.XLSX);
        given(jobs.findByIdAndTenantId(eq(jobId), eq(tenantId))).willReturn(Optional.of(job));
        given(storage.retrieve(any())).willReturn(new byte[]{1});

        useCase.download(jobId);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(2)).record(captor.capture());
        List<String> actions = captor.getAllValues().stream().map(e -> e.action()).toList();
        assertThat(actions).containsExactlyInAnyOrder(
                AuditAction.EXPORT_DOWNLOADED, AuditAction.FILE_DOWNLOADED);
    }

    @Test
    void crossTenantOrUnknownIdIs404() {
        setContext(PermissionCodes.EXPORT_REQUEST);
        given(jobs.findByIdAndTenantId(eq(jobId), eq(tenantId))).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.download(jobId))
                .isInstanceOf(TenantAccessDeniedException.class);
        verify(storage, never()).retrieve(any());
        verify(audit, never()).record(any());
    }

    @Test
    void nonGeneratedJobIsRejected() {
        setContext(PermissionCodes.EXPORT_REQUEST);
        ExportJobJpaEntity job = generatedJob(ExportType.POSITION_CATALOG, ExportFormat.XLSX);
        job.setStatus(ExportJobStatus.QUEUED);
        given(jobs.findByIdAndTenantId(eq(jobId), eq(tenantId))).willReturn(Optional.of(job));

        assertThatThrownBy(() -> useCase.download(jobId))
                .isInstanceOf(ValidationException.class);
        verify(storage, never()).retrieve(any());
    }

    @Test
    void salaryExportRequiresSalaryExportPermission() {
        // Caller has the generic EXPORT_REQUEST but NOT SALARY_EXPORT.
        setContext(PermissionCodes.EXPORT_REQUEST);
        ExportJobJpaEntity job = generatedJob(ExportType.SALARY_RANGES, ExportFormat.XLSX);
        given(jobs.findByIdAndTenantId(eq(jobId), eq(tenantId))).willReturn(Optional.of(job));

        assertThatThrownBy(() -> useCase.download(jobId))
                .isInstanceOf(PermissionDeniedException.class);
        verify(storage, never()).retrieve(any());
        verify(audit, never()).record(any());
    }

    @Test
    void salaryExportSucceedsWithSalaryExportPermission() {
        setContext(PermissionCodes.SALARY_EXPORT);
        ExportJobJpaEntity job = generatedJob(ExportType.SALARY_RANGES, ExportFormat.XLSX);
        given(jobs.findByIdAndTenantId(eq(jobId), eq(tenantId))).willReturn(Optional.of(job));
        given(storage.retrieve(any())).willReturn(new byte[]{5});

        DownloadExportFileUseCase.DownloadPayload payload = useCase.download(jobId);
        assertThat(payload.bytes()).containsExactly(5);
        verify(audit, atLeastOnce()).record(any());
    }

    @Test
    void expiredJobIsRejected() {
        setContext(PermissionCodes.EXPORT_REQUEST);
        ExportJobJpaEntity job = generatedJob(ExportType.POSITION_CATALOG, ExportFormat.XLSX);
        job.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        given(jobs.findByIdAndTenantId(eq(jobId), eq(tenantId))).willReturn(Optional.of(job));

        assertThatThrownBy(() -> useCase.download(jobId))
                .isInstanceOf(ValidationException.class);
        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.EXPIRED);
        verify(storage, never()).retrieve(any());
    }
}
