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
        // Grading layout leading columns (localized human labels, not keys):
        // Position code · Department · Division · Position · Evaluator · Role ·
        // Methodology · Status · Evaluation date.
        assertThat(headers).startsWith(
                "Position code", "Department", "Division", "Position",
                "Evaluator (full name)", "Evaluator role", "Methodology",
                "Status", "Evaluation date");
    }

    @Test
    void xlsxRowsShowResolvedNamesNotEnumsOrUuids() {
        byte[] xlsx = renderXlsx(template, ctx(ReportFormat.XLSX, "en-US"));
        List<List<String>> rows = cells(xlsx);
        assertThat(rows).isNotEmpty();
        // First evaluator row: resolved department + status + evaluator name + role.
        assertThat(rows.get(0)).contains(
                "IT department", "Approved", "Alice Director", "HR director");
        assertThat(rows.stream().flatMap(List::stream))
                .noneMatch(v -> v.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-.*")) // no UUID leak
                .doesNotContain("APPROVED", "DRAFT", "HR_DIRECTOR"); // no raw enum leak
    }

    @Test
    void xlsxPanelOfThreeYieldsThreeRowsThenOneAverageRow() {
        // PANEL_A has 3 evaluators + a materialized average ⇒ 3 evaluator rows then
        // 1 average row carrying the localized "Average" label in the FIO column.
        byte[] xlsx = renderXlsx(template, ctx(ReportFormat.XLSX, "en-US"));
        List<List<String>> rows = cells(xlsx);
        // Rows 0..2 are the panel evaluators (position code POS-001 on EVERY row —
        // no grouping/blanking), row 3 is the average.
        assertThat(rows.get(0)).contains("POS-001", "Alice Director");
        assertThat(rows.get(1)).contains("POS-001", "Bob Manager");
        assertThat(rows.get(2)).contains("POS-001", "Carol Expert");
        // Average row: FIO column carries the localized average label, position code blank.
        assertThat(rows.get(3)).contains("Average");
        assertThat(rows.get(3).get(0)).isEmpty(); // position code blank on the average row
    }

    @Test
    void xlsxWithinPanelOrderedByEvaluatorRoleOrdinalThenName() {
        // EvaluatorRole ordinal: HR_DIRECTOR(0) < DEPARTMENT_DIRECTOR(1) <
        // EXTERNAL_EXPERT(2). The fixture is supplied in that order; the render
        // preserves it within the panel group.
        byte[] xlsx = renderXlsx(template, ctx(ReportFormat.XLSX, "en-US"));
        List<List<String>> rows = cells(xlsx);
        assertThat(rows.get(0)).contains("HR director");
        assertThat(rows.get(1)).contains("Department director");
        assertThat(rows.get(2)).contains("External expert");
    }

    @Test
    void xlsxStandaloneAndAwaitingPanelHaveNoAverageRow() {
        // POS-002 (PANEL_B, AWAITING — no materialized average) and POS-003
        // (null-panel DRAFT) are single rows with NO following "Average" row.
        byte[] xlsx = renderXlsx(template, ctx(ReportFormat.XLSX, "en-US"));
        List<List<String>> rows = cells(xlsx);
        // Exactly ONE average row in the whole sheet (only PANEL_A).
        long avgRows = rows.stream()
                .filter(r -> r.contains("Average"))
                .count();
        assertThat(avgRows).isEqualTo(1);
        // POS-002 row present, immediately followed by POS-003 (no average between).
        int pos2 = indexOfRowContaining(rows, "POS-002");
        assertThat(pos2).isGreaterThanOrEqualTo(0);
        assertThat(rows.get(pos2 + 1)).contains("POS-003");
    }

    @Test
    void xlsxDraftEvaluationHasBlankEvaluationDate() {
        // POS-003 is a DRAFT (submittedAt null in the port) ⇒ blank evaluation date.
        byte[] xlsx = renderXlsx(template, ctx(ReportFormat.XLSX, "en-US"));
        List<List<String>> rows = cells(xlsx);
        List<String> headers = headerRow(xlsx);
        int dateCol = headers.indexOf("Evaluation date");
        int pos3 = indexOfRowContaining(rows, "POS-003");
        assertThat(rows.get(pos3).get(dateCol)).isEmpty();
        // A submitted evaluator row (POS-001) carries the formatted date.
        assertThat(rows.get(0).get(dateCol)).isEqualTo("2026-05-10");
    }

    @Test
    void xlsxDivisionFilledWhenNestedBlankWhenTopLevel() {
        // POS-001 sits under a child department ⇒ Bo'limi (Division) filled.
        // POS-002 sits under a top-level department ⇒ Division blank.
        byte[] xlsx = renderXlsx(template, ctx(ReportFormat.XLSX, "en-US"));
        List<List<String>> rows = cells(xlsx);
        List<String> headers = headerRow(xlsx);
        int divCol = headers.indexOf("Division");
        assertThat(rows.get(0).get(divCol)).isEqualTo("Backend division");
        int pos2 = indexOfRowContaining(rows, "POS-002");
        assertThat(rows.get(pos2).get(divCol)).isEmpty();
    }

    @Test
    void pdfDocxKeepCondensedColumnsUnchanged() {
        // The condensed PDF/DOCX path must NOT gain the grading XLSX columns.
        String docx = docxText(render(template, ctx(ReportFormat.DOCX, "en-US")));
        // Condensed headers are present...
        assertThat(docx).contains("Position code", "Department", "Status", "Total", "Grade");
        // ...and the grading-only XLSX columns are NOT in PDF/DOCX.
        assertThat(docx).doesNotContain(
                "Evaluator (full name)", "Evaluator role", "Evaluation date", "Division");
    }

    private static int indexOfRowContaining(List<List<String>> rows, String value) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).contains(value)) return i;
        }
        return -1;
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
