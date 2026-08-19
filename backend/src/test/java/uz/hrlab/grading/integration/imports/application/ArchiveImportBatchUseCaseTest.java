package uz.hrlab.grading.integration.imports.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.integration.imports.api.ImportBatchResponse;
import uz.hrlab.grading.integration.imports.domain.ImportBatchStatus;
import uz.hrlab.grading.integration.imports.domain.ImportBatchTransitionRejectedException;
import uz.hrlab.grading.integration.imports.domain.ImportTemplateCode;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchJpaEntity;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Orchestration test for {@link ArchiveImportBatchUseCase} — mirrors
 * {@code CancelImportBatchUseCase}'s test coverage shape (tenant scoping,
 * transition-policy enforcement, audit emission).
 *
 * <p>Archive is the retention-only terminal action a user needs for a
 * PARTIALLY_COMMITTED / COMMITTED batch — states {@link
 * CancelImportBatchUseCase} can no longer touch because the rows are already
 * live (integration-blueprint §8.1).
 */
@Tag("imports")
class ArchiveImportBatchUseCaseTest {

    private ImportBatchRepository batches;
    private AuditService audit;
    private ArchiveImportBatchUseCase useCase;

    private UUID tenantId;
    private UUID projectId;
    private UUID userId;
    private UUID batchId;

    @BeforeEach
    void setUp() {
        batches = mock(ImportBatchRepository.class);
        audit = mock(AuditService.class);
        useCase = new ArchiveImportBatchUseCase(batches, audit);

        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();
        batchId = UUID.randomUUID();
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of(PermissionCodes.ORG_IMPORT, PermissionCodes.IMPORT_CANCEL),
                Set.of(), false, "ru-RU"));

        given(batches.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    private ImportBatchJpaEntity batchWithStatus(ImportBatchStatus status) {
        ImportBatchJpaEntity batch = new ImportBatchJpaEntity(batchId, tenantId, projectId,
                ImportTemplateCode.ORG_STRUCTURE_V1, status,
                "file.xlsx", "tenants/x/imports/y", 1024L, "checksum",
                userId, OffsetDateTime.now());
        return batch;
    }

    @Test
    void partiallyCommittedArchivesSuccessfully_andAudits() {
        ImportBatchJpaEntity batch = batchWithStatus(ImportBatchStatus.PARTIALLY_COMMITTED);
        given(batches.findByIdAndTenantId(eq(batchId), eq(tenantId)))
                .willReturn(Optional.of(batch));

        ImportBatchResponse out = useCase.archive(batchId);

        assertThat(out.status()).isEqualTo(ImportBatchStatus.ARCHIVED);
        assertThat(batch.getStatus()).isEqualTo(ImportBatchStatus.ARCHIVED);

        ArgumentCaptor<AuditEvent> auditCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(auditCap.capture());
        assertThat(auditCap.getValue().action()).isEqualTo(AuditAction.IMPORT_ARCHIVED);
        assertThat(auditCap.getValue().entityId()).isEqualTo(batchId);
    }

    @Test
    void committedArchivesSuccessfully() {
        ImportBatchJpaEntity batch = batchWithStatus(ImportBatchStatus.COMMITTED);
        given(batches.findByIdAndTenantId(eq(batchId), eq(tenantId)))
                .willReturn(Optional.of(batch));

        ImportBatchResponse out = useCase.archive(batchId);

        assertThat(out.status()).isEqualTo(ImportBatchStatus.ARCHIVED);
    }

    @Test
    void nonArchivableSourceState_throwsTransitionRejected() {
        ImportBatchJpaEntity batch = batchWithStatus(ImportBatchStatus.UPLOADED);
        given(batches.findByIdAndTenantId(eq(batchId), eq(tenantId)))
                .willReturn(Optional.of(batch));

        assertThatThrownBy(() -> useCase.archive(batchId))
                .isInstanceOf(ImportBatchTransitionRejectedException.class);

        assertThat(batch.getStatus()).isEqualTo(ImportBatchStatus.UPLOADED);
        verify(batches, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void tenantMismatch_throwsTenantAccessDenied() {
        given(batches.findByIdAndTenantId(eq(batchId), eq(tenantId)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.archive(batchId))
                .isInstanceOf(TenantAccessDeniedException.class);

        verify(batches, never()).save(any());
        verify(audit, never()).record(any());
    }
}
