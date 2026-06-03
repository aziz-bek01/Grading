package uz.hrlab.grading.integration.excel;

import org.junit.jupiter.api.Test;

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
}
