package uz.hrlab.grading.reporting.application.template;

import org.junit.jupiter.api.Test;
import uz.hrlab.grading.integration.excel.ExcelWriter;
import uz.hrlab.grading.reporting.application.template.impl.EvaluationSummaryReport;
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

class EvaluationSummaryReportTest {

    private final EvaluationSummaryReport template =
            new EvaluationSummaryReport(new FakeReportDataPort(), new ExcelWriter(),
                    new com.fasterxml.jackson.databind.ObjectMapper());

    @Test
    void typeAndFormats() {
        assertThat(template.reportType()).isEqualTo(ReportType.EVALUATION_SUMMARY);
        assertThat(template.supports(ReportFormat.PDF)).isTrue();
        assertThat(template.supports(ReportFormat.DOCX)).isTrue();
        assertThat(template.supports(ReportFormat.XLSX)).isTrue();
    }

    @Test
    void xlsxFactorHeaderShowsHumanNameNotJustCode() {
        // Delta 1: XLSX factor column header = "name (CODE)" — the human NAME must
        // be present, while the score lookup stays keyed by the stable code.
        byte[] xlsx = renderXlsx(template, ctx(ReportFormat.XLSX, "en-US"));
        List<String> headers = headerRow(xlsx);
        assertThat(headers).contains(
                "Knowledge (KNOWLEDGE)",
                "Problem solving (PROBLEM_SOLVING)",
                "Accountability (ACCOUNTABILITY)");
        // Leading columns are localized human labels, not snake_case keys.
        assertThat(headers).startsWith("Position code", "Position", "Department", "Status");
    }

    @Test
    void xlsxRowsShowResolvedNamesNotEnumsOrUuids() {
        byte[] xlsx = renderXlsx(template, ctx(ReportFormat.XLSX, "en-US"));
        List<List<String>> rows = cells(xlsx);
        assertThat(rows).isNotEmpty();
        // department + grade name + status render as resolved human values.
        assertThat(rows.get(0)).contains("IT department", "Approved", "Operational");
        assertThat(rows.stream().flatMap(List::stream))
                .noneMatch(v -> v.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-.*")) // no UUID leak
                .doesNotContain("APPROVED", "DRAFT"); // no raw enum leak
    }

    @Test
    void docxMetaShowsProjectAndMethodologyNamesNotUuids() {
        String text = docxText(render(template, ctx(ReportFormat.DOCX, "en-US")));
        // The meta lines carry the human project + methodology NAME from the port,
        // never a "methodology_version=<UUID>" fallback (delta 2).
        assertThat(text).contains("Fixture project");
        assertThat(text).contains("Classic 8-factor (v2)");
        assertThat(text).doesNotContain("methodology_version=");
    }

    @Test
    void docxOmitsFilterMetaLinesWhenNoFilter() {
        String text = docxText(render(template, ctx(ReportFormat.DOCX, "en-US")));
        // No applied filter ⇒ no Period / Evaluators / Methodologies meta lines.
        assertThat(text).doesNotContain("Evaluators", "Methodologies");
    }

    @Test
    void docxRendersLocalizedFilterMetaLinesWhenFilterApplied() {
        // A non-empty filter_params drives FilterEcho → meta lines must render
        // with localized labels (AC-4.3). Note these labels also collide with the
        // factor "Knowledge" etc — assert the specific filter LABELS + VALUES.
        ReportGenerationContext ctx = ReportGenerationContext.builder()
                .reportId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .reportType(ReportType.EVALUATION_SUMMARY)
                .format(ReportFormat.DOCX)
                .locale("en-US")
                .filterParams("{\"date_from\":\"2026-04-01\",\"date_to\":\"2026-06-30\","
                        + "\"evaluator_user_ids\":[\"" + UUID.randomUUID() + "\"]}")
                .requestedBy(UUID.randomUUID())
                .requestedAt(OffsetDateTime.now())
                .title("Evaluation summary")
                .build();

        String text = docxText(render(template, ctx));
        assertThat(text).contains("Period", "2026-04-01 – 2026-06-30");
        assertThat(text).contains("Evaluators", "Aliyev A.");
        assertThat(text).contains("Methodologies");
    }

    private ReportGenerationContext ctx(ReportFormat format, String locale) {
        return ReportGenerationContext.builder()
                .reportId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .reportType(ReportType.EVALUATION_SUMMARY)
                .format(format)
                .locale(locale)
                .requestedBy(UUID.randomUUID())
                .requestedAt(OffsetDateTime.now())
                .title("Evaluation summary")
                .build();
    }
}
