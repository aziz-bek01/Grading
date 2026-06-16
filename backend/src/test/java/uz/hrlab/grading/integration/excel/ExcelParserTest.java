package uz.hrlab.grading.integration.excel;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelParserTest {

    private final ExcelWriter writer = new ExcelWriter();
    private final ExcelParser parser = new ExcelParser();

    @Test
    void parseReturnsHeadersAndRows() {
        byte[] xlsx = writer.write("Test",
                List.of("id", "name"),
                List.of(Map.of("id", "1", "name", "Alpha"),
                        Map.of("id", "2", "name", "Beta")));
        ExcelParser.ParsedSheet sheet = parser.parse(xlsx);
        assertThat(sheet.headers()).containsExactly("id", "name");
        assertThat(sheet.rows()).hasSize(2);
        assertThat(sheet.rows().get(0)).containsEntry("name", "Alpha");
    }

    @Test
    void emptyBytesRejected() {
        assertThatThrownBy(() -> parser.parse(new byte[0]))
                .isInstanceOf(ExcelParseException.class);
    }

    @Test
    void nonXlsxRejected() {
        assertThatThrownBy(() -> parser.parse("not an xlsx".getBytes()))
                .isInstanceOf(ExcelParseException.class);
    }

    @Test
    void parsedRowsRoundTripWithSanitisation() {
        byte[] xlsx = writer.write("Test",
                List.of("payload"),
                List.of(Map.of("payload", "=cmd|'/C calc'!A1")));
        ExcelParser.ParsedSheet sheet = parser.parse(xlsx);
        // Sanitised on write — parser sees the safe form.
        assertThat(sheet.rows().get(0).get("payload")).startsWith("'=cmd");
    }

    // -----------------------------------------------------------------
    // Batch 5 — import-side formula-injection neutralisation.
    // The parser is the single choke point: a RAW (not ExcelWriter-written)
    // workbook whose cells genuinely start with a dangerous prefix must be
    // neutralised ON READ, before it is staged / committed / echoed back.
    // -----------------------------------------------------------------

    @Test
    void rawMaliciousTextCellsAreNeutralisedOnParse() throws Exception {
        // Build the workbook with raw POI so the dangerous prefixes are NOT
        // already neutralised by the export-side SafeCellWriter — this proves
        // the import side does the work independently.
        byte[] xlsx = rawWorkbook(List.of("name", "note"), List.of(
                List.of("=cmd|'/C calc'!A1", "@SUM(1+1)"),
                List.of("-2+3+cmd", "+1+1")));
        ExcelParser.ParsedSheet sheet = parser.parse(xlsx);

        Map<String, String> r0 = sheet.rows().get(0);
        Map<String, String> r1 = sheet.rows().get(1);
        assertThat(r0.get("name")).isEqualTo("'=cmd|'/C calc'!A1");
        assertThat(r0.get("note")).isEqualTo("'@SUM(1+1)");
        assertThat(r1.get("name")).isEqualTo("'-2+3+cmd");
        assertThat(r1.get("note")).isEqualTo("'+1+1");
    }

    @Test
    void rawMaliciousHeaderCellIsNeutralisedOnParse() throws Exception {
        // A header like "=cmd" is echoed into validation error messages that may
        // be exported as a CSV error report — it must be neutralised on read.
        byte[] xlsx = rawWorkbook(List.of("=cmd", "name"),
                List.of(List.of("1", "Alpha")));
        ExcelParser.ParsedSheet sheet = parser.parse(xlsx);
        assertThat(sheet.headers().get(0)).isEqualTo("'=cmd");
        assertThat(sheet.headers().get(1)).isEqualTo("name");
    }

    @Test
    void negativeNumberCellIsPreservedNotPrefixed() throws Exception {
        // A NUMBER (not text) must survive intact — prefixing "-5" would corrupt
        // a legitimate negative value. POI types it NUMERIC, so the parser must
        // NOT route it through the sanitiser.
        byte[] xlsx;
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Test");
            Row h = sheet.createRow(0);
            h.createCell(0).setCellValue("amount");
            Row r = sheet.createRow(1);
            r.createCell(0).setCellValue(-5d); // numeric, not a string
            wb.write(out);
            xlsx = out.toByteArray();
        }
        ExcelParser.ParsedSheet sheet = parser.parse(xlsx);
        assertThat(sheet.rows().get(0).get("amount")).isEqualTo("-5");
        assertThat(sheet.rows().get(0).get("amount")).doesNotStartWith("'");
    }

    @Test
    void leadingZeroTextCodeIsPreservedOnParse() throws Exception {
        // A code like "0042" arrives as text starting with a digit — never a
        // dangerous prefix — so it must be passed through unchanged.
        byte[] xlsx = rawWorkbook(List.of("code"), List.of(List.of("0042")));
        ExcelParser.ParsedSheet sheet = parser.parse(xlsx);
        assertThat(sheet.rows().get(0).get("code")).isEqualTo("0042");
    }

    /**
     * Build an .xlsx using RAW Apache POI string cells (bypassing
     * {@link ExcelWriter}/{@link SafeCellWriter}) so test payloads reach the
     * parser un-neutralised. Every cell is set as an explicit text value.
     */
    private static byte[] rawWorkbook(List<String> headers, List<List<String>> rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Test");
            Row header = sheet.createRow(0);
            for (int c = 0; c < headers.size(); c++) {
                header.createCell(c, org.apache.poi.ss.usermodel.CellType.STRING)
                        .setCellValue(headers.get(c));
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<String> cells = rows.get(r);
                for (int c = 0; c < cells.size(); c++) {
                    row.createCell(c, org.apache.poi.ss.usermodel.CellType.STRING)
                            .setCellValue(cells.get(c));
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    // -----------------------------------------------------------------
    // Sheet selection — guide sheet (sheet #2) must be ignored on upload
    // -----------------------------------------------------------------

    @Test
    void parserReadsDataSheetWhenGuideSheetPresentAtIndex1() {
        GuideSheet guide = new GuideSheet(
                ExcelParserTest.guideName(),
                new int[] { 20, 20 },
                List.of(GuideSheet.GuideRow.header("Instruction", "Text"),
                        GuideSheet.GuideRow.body("Fill column id", "with a number")));
        byte[] xlsx = writer.write("Departments",
                List.of("id", "name"),
                List.of(Map.of("id", "1", "name", "Alpha")),
                guide);

        ExcelParser.ParsedSheet sheet = parser.parse(xlsx);
        assertThat(sheet.headers()).containsExactly("id", "name");
        // Guide sheet contributes no data rows.
        assertThat(sheet.rows()).hasSize(1);
        assertThat(sheet.rows().get(0)).containsEntry("name", "Alpha");
    }

    @Test
    void parserSkipsGuideSheetEvenWhenItIsPhysicallyFirst() throws Exception {
        // Defensive: a user could re-order sheets so the guide sits at index 0.
        // The parser must still resolve the data sheet by skipping the guide.
        byte[] xlsx = workbookWithGuideFirstThenData();
        ExcelParser.ParsedSheet sheet = parser.parse(xlsx);
        assertThat(sheet.headers()).containsExactly("id", "name");
        assertThat(sheet.rows()).hasSize(1);
        assertThat(sheet.rows().get(0)).containsEntry("name", "Beta");
    }

    /** The guide sheet name the parser is told to skip. */
    private static String guideName() {
        return "Йўриқнома-Инструкция";
    }

    /** Build a workbook where sheet 0 = guide, sheet 1 = data. */
    private static byte[] workbookWithGuideFirstThenData() throws Exception {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet guide = wb.createSheet(guideName());
            Row gh = guide.createRow(0);
            gh.createCell(0).setCellValue("Instruction column");
            Row gb = guide.createRow(1);
            gb.createCell(0).setCellValue("Some guidance text");

            Sheet data = wb.createSheet("Departments");
            Row header = data.createRow(0);
            header.createCell(0).setCellValue("id");
            header.createCell(1).setCellValue("name");
            Row r1 = data.createRow(1);
            r1.createCell(0).setCellValue("9");
            r1.createCell(1).setCellValue("Beta");

            wb.write(out);
            return out.toByteArray();
        }
    }
}
