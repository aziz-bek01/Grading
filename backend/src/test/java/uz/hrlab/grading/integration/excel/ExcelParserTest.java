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
