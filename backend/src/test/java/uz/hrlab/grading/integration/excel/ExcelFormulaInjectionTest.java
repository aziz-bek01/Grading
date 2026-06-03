package uz.hrlab.grading.integration.excel;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SECURITY TEST — formula injection protection (integration-blueprint §13.1).
 *
 * <p>Every user-input cell with a dangerous prefix must be neutralised on
 * export. The test asserts that {@link SafeCellWriter#sanitize(String)}
 * prefixes the value with a single apostrophe so spreadsheet engines treat
 * the content as literal text rather than as a formula.
 */
@Tag("security")
class ExcelFormulaInjectionTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "=cmd|'/C calc'!A1",
            "=SUM(1+1)",
            "+1+1",
            "-1-2",
            "@SUM(1+1)",
            "\t=cmd",
            "\r=cmd",
            "\n=cmd"
    })
    void dangerousPrefixesAreSanitized(String payload) {
        String safe = SafeCellWriter.sanitize(payload);
        assertThat(safe).startsWith("'");
        assertThat(safe.substring(1)).isEqualTo(payload);
        assertThat(SafeCellWriter.wouldSanitize(payload)).isTrue();
    }

    @Test
    void plainTextIsNotSanitized() {
        assertThat(SafeCellWriter.sanitize("Hello World")).isEqualTo("Hello World");
        assertThat(SafeCellWriter.sanitize("1234")).isEqualTo("1234");
        assertThat(SafeCellWriter.sanitize("a+b")).isEqualTo("a+b"); // not at start
        assertThat(SafeCellWriter.wouldSanitize("Hello World")).isFalse();
    }

    @Test
    void nullAndEmptyAreUntouched() {
        assertThat(SafeCellWriter.sanitize(null)).isNull();
        assertThat(SafeCellWriter.sanitize("")).isEqualTo("");
    }

    @Test
    void leadingSpacesDoNotBypassSanitization() {
        assertThat(SafeCellWriter.sanitize("   =CMD()")).startsWith("'");
    }

    @Test
    void excelWriterApplliesSanitizationOnFormulaPayload() throws Exception {
        ExcelWriter writer = new ExcelWriter();
        byte[] bytes = writer.write("Test",
                List.of("name"),
                List.of(Map.of("name", "=cmd|'/C calc'!A1")));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            Row row = sheet.getRow(1);
            String value = row.getCell(0).getStringCellValue();
            assertThat(value).startsWith("'=cmd");
        }
    }

    @Test
    void excelWriterPreservesBenignContent() throws Exception {
        ExcelWriter writer = new ExcelWriter();
        byte[] bytes = writer.write("Test",
                List.of("name"),
                List.of(Map.of("name", "Project Alpha")));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            String v = wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue();
            assertThat(v).isEqualTo("Project Alpha");
        }
    }
}
