package uz.hrlab.grading.integration.validation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.integration.excel.ExcelParser;
import uz.hrlab.grading.integration.imports.domain.ImportErrorLevel;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Coverage for the 5-level pipeline (integration-blueprint §11). */
@Tag("workflow")
class ImportValidatorTest {

    private final ImportValidator validator = new ImportValidator();

    // -------- Level 1 — File validation -------------------------------------

    @Test
    void emptyFileIsBlocker() {
        ValidationResult r = validator.validateFile(ImportValidator.XLSX_MIME, "ok.xlsx", 0L);
        assertThat(r.hasBlockers()).isTrue();
    }

    @Test
    void oversizedFileIsBlocker() {
        ValidationResult r = validator.validateFile(ImportValidator.XLSX_MIME, "ok.xlsx",
                ImportValidator.MAX_FILE_SIZE_BYTES + 1);
        assertThat(r.hasBlockers()).isTrue();
        assertThat(r.findings().stream().anyMatch(f -> "FILE_TOO_LARGE".equals(f.code()))).isTrue();
    }

    @Test
    void wrongExtensionIsBlocker() {
        ValidationResult r = validator.validateFile(ImportValidator.XLSX_MIME, "evil.xlsm", 100L);
        assertThat(r.hasBlockers()).isTrue();
    }

    @Test
    void pathTraversalInFilenameIsBlocker() {
        ValidationResult r = validator.validateFile(ImportValidator.XLSX_MIME, "../../etc/passwd.xlsx", 100L);
        assertThat(r.hasBlockers()).isTrue();
    }

    @Test
    void wrongMimeIsBlocker() {
        ValidationResult r = validator.validateFile("application/octet-stream", "ok.xlsx", 100L);
        assertThat(r.hasBlockers()).isTrue();
    }

    @Test
    void validFilePasses() {
        ValidationResult r = validator.validateFile(ImportValidator.XLSX_MIME, "good.xlsx", 1024L);
        assertThat(r.hasBlockers()).isFalse();
    }

    // -------- Level 2 — Structure validation --------------------------------

    @Test
    void missingRequiredColumnIsBlocker() {
        ExcelParser.ParsedSheet sheet = new ExcelParser.ParsedSheet(
                List.of("external_id"),
                List.of(Map.of("external_id", "A")));
        ValidationResult r = validator.validateStructure(sheet, List.of("external_id", "name"));
        assertThat(r.hasBlockers()).isTrue();
    }

    @Test
    void structureValidatesWhenAllColumnsPresent() {
        ExcelParser.ParsedSheet sheet = new ExcelParser.ParsedSheet(
                List.of("external_id", "name"),
                List.of(Map.of("external_id", "A", "name", "Alpha")));
        ValidationResult r = validator.validateStructure(sheet, List.of("external_id", "name"));
        assertThat(r.hasBlockers()).isFalse();
    }

    // -------- Level 3 — Row validation --------------------------------------

    @Test
    void emptyRequiredFieldIsError() {
        ExcelParser.ParsedSheet sheet = new ExcelParser.ParsedSheet(
                List.of("name"),
                List.of(Map.of("name", "")));
        ValidationResult r = validator.validateRows(sheet, List.of("name"));
        assertThat(r.countByLevel(ImportErrorLevel.ERROR)).isEqualTo(1);
    }

    // -------- Level 5 — Security validation ---------------------------------

    @Test
    void missingPermissionIsBlocker() {
        ExcelParser.ParsedSheet sheet = new ExcelParser.ParsedSheet(
                List.of("name"), List.of(Map.of("name", "Alpha")));
        ValidationResult r = validator.validateSecurity(sheet, false, List.of("name"));
        assertThat(r.hasBlockers()).isTrue();
    }

    @Test
    void tenantIdColumnInFileIsIgnoredWithWarning() {
        ExcelParser.ParsedSheet sheet = new ExcelParser.ParsedSheet(
                List.of("tenant_id", "name"),
                List.of(Map.of("tenant_id", UUID.randomUUID().toString(), "name", "Alpha")));
        ValidationResult r = validator.validateSecurity(sheet, true, List.of("name"));
        assertThat(r.countByLevel(ImportErrorLevel.WARNING)).isEqualTo(1);
        assertThat(r.hasBlockers()).isFalse();
    }

    @Test
    void formulaInputInUserCellRaisesWarning() {
        ExcelParser.ParsedSheet sheet = new ExcelParser.ParsedSheet(
                List.of("name"),
                List.of(Map.of("name", "=cmd|'/C calc'!A1")));
        ValidationResult r = validator.validateSecurity(sheet, true, List.of("name"));
        assertThat(r.countByLevel(ImportErrorLevel.WARNING)).isEqualTo(1);
        assertThat(r.findings().get(0).code()).isEqualTo("FORMULA_LIKE_INPUT_SANITIZED");
    }
}
