package uz.hrlab.grading.reporting.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.reporting.domain.ReportFormat;
import uz.hrlab.grading.reporting.domain.ReportType;
import uz.hrlab.grading.reporting.infrastructure.ReportGenerationJob;
import uz.hrlab.grading.reporting.infrastructure.ReportRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Audit reason composition (NFR-5 / decision D5): the REPORT_REQUESTED reason
 * carries filter CARDINALITY COUNTS only — never raw ids / names. Empty filter
 * preserves the legacy reason shape (no regression).
 */
class RequestReportUseCaseAuditTest {

    private final ReportRepository reports = mock(ReportRepository.class);
    private final ReportGenerationJob worker = mock(ReportGenerationJob.class);
    private final AuditService audit = mock(AuditService.class);

    private RequestReportUseCase newUseCase() {
        EvaluationReportFilterValidator validator =
                mock(EvaluationReportFilterValidator.class);
        // Validator parses with a real ObjectMapper so the use case receives the
        // structured filter (we only need the cardinality wiring here).
        when(validator.validate(any(), any(), any())).thenAnswer(inv ->
                uz.hrlab.grading.reporting.application.template.EvaluationReportFilter
                        .parse(inv.getArgument(2), new com.fasterxml.jackson.databind.ObjectMapper()));
        return new RequestReportUseCase(reports, worker, audit, validator);
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    private void actWithReportCreate() {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), UUID.randomUUID(), Set.of(UUID.randomUUID()),
                Set.of("HR_DIRECTOR"), Set.of("REPORT_CREATE"),
                Set.of(), false, "ru-RU"));
    }

    @Test
    void emptyFilterKeepsLegacyReasonShape() {
        actWithReportCreate();
        newUseCase().request(ReportType.EVALUATION_SUMMARY, ReportFormat.PDF,
                UUID.randomUUID(), null);

        ArgumentCaptor<AuditEvent> cap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(cap.capture());
        assertThat(cap.getValue().reason())
                .isEqualTo("type=EVALUATION_SUMMARY format=PDF")
                .doesNotContain("filters=");
    }

    @Test
    void appliedFilterAppendsCardinalityCountsOnly() {
        actWithReportCreate();
        UUID mv1 = UUID.randomUUID();
        UUID mv2 = UUID.randomUUID();
        UUID ev1 = UUID.randomUUID();
        String json = ("{\"methodology_version_ids\":[\"%s\",\"%s\"],"
                + "\"evaluator_user_ids\":[\"%s\"],"
                + "\"date_from\":\"2026-04-01\",\"date_to\":\"2026-06-30\"}")
                .formatted(mv1, mv2, ev1);

        newUseCase().request(ReportType.EVALUATION_SUMMARY, ReportFormat.XLSX,
                UUID.randomUUID(), json);

        ArgumentCaptor<AuditEvent> cap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(cap.capture());
        String reason = cap.getValue().reason();
        assertThat(reason).contains(
                "filters={methodologyVersions:2,evaluators:1,dateRange:true}");
        // D5 — counts only: no raw UUIDs leak into the forensic reason string.
        assertThat(reason).doesNotContain(mv1.toString(), mv2.toString(), ev1.toString());
    }
}
