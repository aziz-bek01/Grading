package uz.hrlab.grading.reporting.application.template;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.PaneInformation;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.integration.excel.ExcelWriter;
import uz.hrlab.grading.reporting.application.template.impl.EvaluationSummaryReport;
import uz.hrlab.grading.reporting.domain.ReportFormat;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.io.ByteArrayInputStream;
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
        // Average row: the localized average label lives in the MERGED A..F cell —
        // i.e. the top-left (column 0) cell — and the spanned cells (1..5) are blank.
        assertThat(rows.get(3)).contains("Average");
        assertThat(rows.get(3).get(0)).isEqualTo("Average"); // merged label cell (A)
        assertThat(rows.get(3).get(1)).isEmpty(); // spanned cell within the merge
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

    @Test
    void xlsxHeaderRowIsDarkGreenWhiteBoldWrappedAndCentered() throws Exception {
        byte[] xlsx = renderXlsx(template, ctx(ReportFormat.XLSX, "en-US"));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            Cell h0 = sheet.getRow(0).getCell(0);
            XSSFCellStyle style = (XSSFCellStyle) h0.getCellStyle();

            // Solid dark forest-green fill (#1F4E2C).
            assertThat(style.getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
            XSSFColor fill = style.getFillForegroundColorColor();
            assertThat(fill).isNotNull();
            assertThat(toHex(fill.getRGB())).isEqualTo("1F4E2C");

            // White BOLD font + wrap text + center/center.
            XSSFFont font = style.getFont();
            assertThat(font.getBold()).isTrue();
            assertThat(font.getColor()).isEqualTo(IndexedColors.WHITE.getIndex());
            assertThat(style.getWrapText()).isTrue();
            assertThat(style.getAlignment()).isEqualTo(HorizontalAlignment.CENTER);
            assertThat(style.getVerticalAlignment()).isEqualTo(VerticalAlignment.CENTER);

            // Header row taller than default (≈ 3 wrapped lines).
            assertThat(sheet.getRow(0).getHeightInPoints())
                    .isGreaterThan(sheet.getDefaultRowHeightInPoints() * 2);
            // Header cells have the four thin borders too.
            assertThinBorders(style);
        }
    }

    @Test
    void xlsxEveryDataCellHasThinGridBorders() throws Exception {
        byte[] xlsx = renderXlsx(template, ctx(ReportFormat.XLSX, "en-US"));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            // A representative data cell (first evaluator row, first column).
            Cell sample = sheet.getRow(1).getCell(0);
            assertThinBorders((XSSFCellStyle) sample.getCellStyle());
        }
    }

    @Test
    void xlsxFactorAndTotalColumnsAreNumericWithOneDecimalFormat() throws Exception {
        byte[] xlsx = renderXlsx(template, ctx(ReportFormat.XLSX, "en-US"));
        List<String> headers = headerRow(xlsx);
        int factorCol = headers.indexOf("Knowledge (KNOWLEDGE)");
        int totalCol = headers.indexOf("Total");
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            Cell factor = sheet.getRow(1).getCell(factorCol);
            Cell total = sheet.getRow(1).getCell(totalCol);

            assertThat(factor.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(factor.getNumericCellValue()).isEqualTo(60.0d);
            assertThat(factor.getCellStyle().getDataFormatString()).contains("0.0");

            assertThat(total.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(total.getNumericCellValue()).isEqualTo(130.0d);
            assertThat(total.getCellStyle().getDataFormatString()).contains("0.0");

            // A non-numeric column (Evaluator) stays a sanitized STRING cell.
            int evalCol = headers.indexOf("Evaluator (full name)");
            assertThat(sheet.getRow(1).getCell(evalCol).getCellType())
                    .isEqualTo(CellType.STRING);
        }
    }

    @Test
    void xlsxAverageRowIsBoldGreenMergedAcrossAtoF() throws Exception {
        byte[] xlsx = renderXlsx(template, ctx(ReportFormat.XLSX, "en-US"));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            // The average row is the 4th body row (index 3) ⇒ sheet row 4.
            int avgRowIdx = -1;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Cell c0 = sheet.getRow(r).getCell(0);
                if (c0 != null && "Average".equals(c0.toString())) { avgRowIdx = r; break; }
            }
            assertThat(avgRowIdx).isGreaterThan(0);

            XSSFCellStyle labelStyle = (XSSFCellStyle) sheet.getRow(avgRowIdx).getCell(0).getCellStyle();
            assertThat(labelStyle.getFont().getBold()).isTrue();
            assertThat(labelStyle.getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
            assertThat(labelStyle.getFillForegroundColor())
                    .isEqualTo(IndexedColors.LIGHT_GREEN.getIndex());
            assertThinBorders(labelStyle);

            // A..F (0..5) merged into the single label cell.
            boolean merged = false;
            for (CellRangeAddress region : sheet.getMergedRegions()) {
                if (region.getFirstRow() == avgRowIdx && region.getLastRow() == avgRowIdx
                        && region.getFirstColumn() == 0 && region.getLastColumn() == 5) {
                    merged = true;
                    break;
                }
            }
            assertThat(merged).as("average row A..F merged region").isTrue();

            // The average factor cell is still numeric + green + bold.
            int factorCol = headerRow(xlsx).indexOf("Knowledge (KNOWLEDGE)");
            Cell avgFactor = sheet.getRow(avgRowIdx).getCell(factorCol);
            assertThat(avgFactor.getCellType()).isEqualTo(CellType.NUMERIC);
            XSSFCellStyle avgNumStyle = (XSSFCellStyle) avgFactor.getCellStyle();
            assertThat(avgNumStyle.getFont().getBold()).isTrue();
            assertThat(avgNumStyle.getDataFormatString()).contains("0.0");
        }
    }

    @Test
    void xlsxFreezesHeaderRowAndSetsColumnWidths() throws Exception {
        byte[] xlsx = renderXlsx(template, ctx(ReportFormat.XLSX, "en-US"));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            PaneInformation pane = sheet.getPaneInformation();
            assertThat(pane).isNotNull();
            assertThat(pane.getHorizontalSplitPosition()).isEqualTo((short) 1);

            // Explicit fixed widths set (wider than POI's default column width).
            int defaultWidth = sheet.getDefaultColumnWidth() * 256;
            assertThat(sheet.getColumnWidth(0)).isGreaterThan(defaultWidth);
            assertThat(sheet.getColumnWidth(4)).isGreaterThan(defaultWidth); // Evaluator
        }
    }

    private static void assertThinBorders(XSSFCellStyle style) {
        assertThat(style.getBorderTop()).isEqualTo(BorderStyle.THIN);
        assertThat(style.getBorderBottom()).isEqualTo(BorderStyle.THIN);
        assertThat(style.getBorderLeft()).isEqualTo(BorderStyle.THIN);
        assertThat(style.getBorderRight()).isEqualTo(BorderStyle.THIN);
    }

    private static String toHex(byte[] rgb) {
        StringBuilder sb = new StringBuilder();
        for (byte b : rgb) sb.append(String.format("%02X", b & 0xFF));
        return sb.toString();
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
