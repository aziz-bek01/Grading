package uz.hrlab.grading.integration.imports.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.integration.excel.ExcelParser;
import uz.hrlab.grading.integration.imports.api.ImportBatchResponse;
import uz.hrlab.grading.integration.imports.domain.ImportBatchStatus;
import uz.hrlab.grading.integration.imports.domain.ImportTemplateCode;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchJpaEntity;
import uz.hrlab.grading.integration.imports.infrastructure.ImportBatchRepository;
import uz.hrlab.grading.integration.imports.infrastructure.ImportErrorJpaEntity;
import uz.hrlab.grading.integration.imports.infrastructure.ImportErrorRepository;
import uz.hrlab.grading.integration.storage.ObjectStorageAdapter;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import static org.mockito.Mockito.verify;

/**
 * PO-11 — orchestration test for {@link CommitImportBatchUseCase}.
 *
 * <p>Uses a fake {@link ImportRowCommitter} that succeeds for "OK-*" rows
 * and throws {@link ImportRowCommitException} for "BAD-*" rows. Asserts the
 * batch lands at PARTIALLY_COMMITTED when both success + failure rows are
 * present, COMMITTED when all succeed, and FAILED when none succeed.
 * Also asserts that templates without a committer reject the commit instead
 * of silently flipping status.
 */
@Tag("imports")
class CommitImportBatchUseCaseTest {

    private ImportBatchRepository batches;
    private ImportErrorRepository errors;
    private ImportTemplateRegistry templates;
    private ObjectStorageAdapter storage;
    private ExcelParser parser;
    private AbacGate abacGate;
    private AuditService audit;
    private ImportRowCommitterRegistry committers;
    private FakeOrgCommitter fakeCommitter;
    private CommitImportBatchUseCase useCase;

    private UUID tenantId;
    private UUID projectId;
    private UUID userId;
    private UUID batchId;
    private ImportBatchJpaEntity batch;

    @BeforeEach
    void setUp() {
        batches = mock(ImportBatchRepository.class);
        errors = mock(ImportErrorRepository.class);
        templates = new ImportTemplateRegistry();
        storage = mock(ObjectStorageAdapter.class);
        parser = mock(ExcelParser.class);
        abacGate = mock(AbacGate.class);
        audit = mock(AuditService.class);
        fakeCommitter = new FakeOrgCommitter();
        committers = new ImportRowCommitterRegistry(List.of(fakeCommitter));
        useCase = new CommitImportBatchUseCase(batches, errors, templates,
                committers, storage, parser, abacGate, audit);

        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();
        batchId = UUID.randomUUID();
        TenantContextHolder.set(new TenantContext(
                userId, tenantId, Set.of(projectId),
                Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of(PermissionCodes.ORG_IMPORT,
                        PermissionCodes.POSITION_IMPORT,
                        PermissionCodes.GRADE_IMPORT),
                Set.of(), false, "ru-RU"));

        batch = new ImportBatchJpaEntity(batchId, tenantId, projectId,
                ImportTemplateCode.ORG_STRUCTURE_V1, ImportBatchStatus.READY_FOR_REVIEW,
                "file.xlsx", "tenants/x/imports/y", 1024L, "checksum",
                userId, OffsetDateTime.now());
        batch.setTraceId("trace-1");
        batch.setTotalRowCount(3);
        batch.setErrorRowCount(0);

        given(batches.findByIdAndTenantId(eq(batchId), eq(tenantId)))
                .willReturn(Optional.of(batch));
        given(batches.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(storage.retrieve(any())).willReturn(new byte[]{1, 2, 3});
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    void allRowsSucceed_batchLandsCOMMITTED() {
        given(parser.parse(any())).willReturn(new ExcelParser.ParsedSheet(
                List.of("external_id", "name"),
                List.of(row("OK-1"), row("OK-2"), row("OK-3"))));

        ImportBatchResponse out = useCase.commit(batchId);
        assertThat(out.status()).isEqualTo(ImportBatchStatus.COMMITTED);
        assertThat(out.committedRowCount()).isEqualTo(3);
        assertThat(fakeCommitter.successCount).isEqualTo(3);
    }

    @Test
    void someRowsFail_batchLandsPARTIALLY_COMMITTED_andErrorsAreRecorded() {
        given(parser.parse(any())).willReturn(new ExcelParser.ParsedSheet(
                List.of("external_id", "name"),
                List.of(row("OK-1"), row("BAD-2"), row("OK-3"))));

        ImportBatchResponse out = useCase.commit(batchId);
        assertThat(out.status()).isEqualTo(ImportBatchStatus.PARTIALLY_COMMITTED);
        assertThat(out.committedRowCount()).isEqualTo(2);

        // One per-row error row was persisted.
        ArgumentCaptor<ImportErrorJpaEntity> errCap = ArgumentCaptor.forClass(ImportErrorJpaEntity.class);
        verify(errors).save(errCap.capture());
        assertThat(errCap.getValue().getMessage()).contains("synthetic-failure");
    }

    @Test
    void zeroRowsSucceed_batchLandsFAILED() {
        given(parser.parse(any())).willReturn(new ExcelParser.ParsedSheet(
                List.of("external_id", "name"),
                List.of(row("BAD-1"), row("BAD-2"))));

        ImportBatchResponse out = useCase.commit(batchId);
        assertThat(out.status()).isEqualTo(ImportBatchStatus.FAILED);
        assertThat(out.committedRowCount()).isZero();
    }

    @Test
    void unsupportedTemplate_throwsValidationException() {
        // Every SHIPPING template now has a committer (JOB_PROFILE_V1 and
        // METHODOLOGY_FACTORS_V1 included), so we can no longer point at a real
        // template to exercise the "no committer registered" rejection. Use a
        // SYNTHETIC template that IS registered (passes the template-lookup +
        // permission gates) but has NO committer — keeping the COMMIT_NOT_SUPPORTED
        // branch genuinely meaningful.
        String fakeCode = "FAKE_NO_COMMITTER_V1";
        ImportTemplateRegistry fakeTemplates = mock(ImportTemplateRegistry.class);
        given(fakeTemplates.find(fakeCode)).willReturn(Optional.of(
                new ImportTemplateDefinition(fakeCode, "Fake (no committer)",
                        List.of("x"), List.of(), List.of("x"), List.of(),
                        PermissionCodes.ORG_IMPORT, "Fake", false)));
        CommitImportBatchUseCase localUseCase = new CommitImportBatchUseCase(
                batches, errors, fakeTemplates, committers, storage, parser, abacGate, audit);

        batch = new ImportBatchJpaEntity(batchId, tenantId, projectId,
                fakeCode, ImportBatchStatus.READY_FOR_REVIEW,
                "file.xlsx", "k", 1024L, "c", userId, OffsetDateTime.now());
        // Caller holds the fake template's required permission (ORG_IMPORT) —
        // already granted by the setUp() context — so the reject is purely about
        // the missing committer, not authz.
        given(batches.findByIdAndTenantId(eq(batchId), eq(tenantId)))
                .willReturn(Optional.of(batch));

        ValidationException err = (ValidationException) org.junit.jupiter.api.Assertions
                .assertThrows(ValidationException.class, () -> localUseCase.commit(batchId));
        assertThat(err.getCode()).isEqualTo("COMMIT_NOT_SUPPORTED");
    }

    @Test
    void finalAuditEvent_emittedWithCorrectAction() {
        given(parser.parse(any())).willReturn(new ExcelParser.ParsedSheet(
                List.of("external_id", "name"),
                List.of(row("OK-1"))));
        useCase.commit(batchId);

        ArgumentCaptor<AuditEvent> auditCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, atLeastOnce()).record(auditCap.capture());
        List<String> actions = auditCap.getAllValues().stream().map(AuditEvent::action).toList();
        assertThat(actions).contains(AuditAction.IMPORT_COMMITTED);
    }

    private static Map<String, String> row(String externalId) {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("external_id", externalId);
        r.put("name", "name-" + externalId);
        return r;
    }

    /** Fake committer — succeeds for "OK-*" rows, throws for "BAD-*" rows. */
    static class FakeOrgCommitter implements ImportRowCommitter {
        int successCount = 0;

        @Override
        public String templateCode() {
            return ImportTemplateCode.ORG_STRUCTURE_V1;
        }

        @Override
        public CommitResult commit(Map<String, String> row, ImportRowCommitContext ctx) {
            String code = row.get("external_id");
            if (code != null && code.startsWith("BAD")) {
                throw new ImportRowCommitException("SYNTH_FAIL", "synthetic-failure for " + code);
            }
            successCount++;
            return new CommitResult("Department", UUID.randomUUID(), AuditAction.DEPARTMENT_CREATED);
        }
    }
}
