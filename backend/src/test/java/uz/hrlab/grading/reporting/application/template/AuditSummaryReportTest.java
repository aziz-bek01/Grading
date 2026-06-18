package uz.hrlab.grading.reporting.application.template;

import org.junit.jupiter.api.Test;
import uz.hrlab.grading.integration.excel.ExcelWriter;
import uz.hrlab.grading.reporting.application.template.impl.AuditSummaryReport;
import uz.hrlab.grading.reporting.domain.ReportFormat;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.hrlab.grading.reporting.application.template.ReportXlsxTestSupport.cells;
import static uz.hrlab.grading.reporting.application.template.ReportXlsxTestSupport.docxText;
import static uz.hrlab.grading.reporting.application.template.ReportXlsxTestSupport.headerRow;
import static uz.hrlab.grading.reporting.application.template.ReportXlsxTestSupport.render;
import static uz.hrlab.grading.reporting.application.template.ReportXlsxTestSupport.renderXlsx;

class AuditSummaryReportTest {

    private final AuditSummaryReport template =
            new AuditSummaryReport(new FakeReportDataPort(), new ExcelWriter());

    @Test
    void typeAndFormats() {
        assertThat(template.reportType()).isEqualTo(ReportType.AUDIT_SUMMARY);
        assertThat(template.supports(ReportFormat.XLSX)).isTrue();
    }

    @Test
    void xlsxActorColumnShowsDisplayNameNotUuidAndKeepsIsoForSiem() {
        byte[] xlsx = renderXlsx(template, ctx(ReportFormat.XLSX, "en-US"));
        assertThat(headerRow(xlsx)).startsWith(
                "Timestamp", "Action", "Actor", "Entity type", "Entity id", "Reason");
        List<List<String>> rows = cells(xlsx);
        assertThat(rows).isNotEmpty();
        // Actor (col 2) is a resolved display name, never a raw UUID.
        assertThat(rows.get(0).get(2)).isEqualTo("Alice Director");
        assertThat(rows.stream().map(r -> r.get(2)))
                .noneMatch(v -> v.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-.*"));
        // SIEM sheet keeps ISO-8601 timestamps + the raw forensic action code.
        assertThat(rows.get(0).get(0)).contains("2026-05-20T");
        assertThat(rows.get(0).get(1)).isEqualTo("EVALUATION_APPROVED");
    }

    @Test
    void docxLocalizesActionAndTimestampForHumans() {
        String text = docxText(render(template, ctx(ReportFormat.DOCX, "en-US")));
        // Human DOCX surface: action localized, NOT the raw forensic enum code.
        assertThat(text).contains("Evaluation approved");
        assertThat(text).doesNotContain("EVALUATION_APPROVED");
        // Timestamp is locale-formatted (month word), not raw ISO with 'T'/'Z'.
        assertThat(text).contains("May");
        assertThat(text).doesNotContain("2026-05-20T");
        // Actor display name, not a UUID.
        assertThat(text).contains("Alice Director");
    }

    private ReportGenerationContext ctx(ReportFormat format, String locale) {
        return ReportGenerationContext.builder()
                .reportId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .reportType(ReportType.AUDIT_SUMMARY)
                .format(format)
                .locale(locale)
                .requestedBy(UUID.randomUUID())
                .requestedAt(OffsetDateTime.now())
                .title("Audit summary")
                .build();
    }
}
