package uz.hrlab.grading.integration.validation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.integration.excel.ExcelParser;
import uz.hrlab.grading.integration.imports.application.ImportTemplateDefinition;
import uz.hrlab.grading.integration.imports.application.ImportTemplateRegistry;
import uz.hrlab.grading.integration.imports.domain.ImportErrorLevel;
import uz.hrlab.grading.integration.imports.domain.ImportTemplateCode;

import java.util.LinkedHashMap;
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

    /**
     * QA-gate finding, now FIXED: {@link ImportTemplateRegistry}'s
     * {@code METHODOLOGY_FACTORS_V1} definition now lists {@code weight} and
     * {@code score} in BOTH {@code requiredColumns} (Level-2 — the COLUMN HEADER
     * must be present) AND {@code requiredFields} (Level-3 — a BLANK CELL in an
     * existing column is flagged). The committer requires both on every row, so
     * a blank {@code weight}/{@code score} cell is now caught up front at
     * upload-time validation instead of surfacing one stage too late as a
     * per-row {@code MISSING_REQUIRED_FIELD} COMMIT failure. This test locks the
     * fix: a blank {@code weight} cell IS a Level-3 blocker.
     */
    @Test
    void methodologyFactorsV1_blankWeightCell_isCaughtAtLevel3RowValidation() {
        ImportTemplateDefinition def = new ImportTemplateRegistry()
                .find(ImportTemplateCode.METHODOLOGY_FACTORS_V1)
                .orElseThrow();

        Map<String, String> rowMissingWeight = new LinkedHashMap<>();
        rowMissingWeight.put("methodology_code", "ACME-GRADING");
        rowMissingWeight.put("methodology_name", "ACME grading");
        rowMissingWeight.put("methodology_type", "CLASSIC_8_FACTOR");
        rowMissingWeight.put("scoring_mode", "WEIGHTED_POINTS");
        rowMissingWeight.put("target_total_points", "1000");
        rowMissingWeight.put("factor_code", "KNOWLEDGE");
        rowMissingWeight.put("factor_name", "Knowledge");
        rowMissingWeight.put("level_code", "L1");
        rowMissingWeight.put("level_name", "Basic");
        rowMissingWeight.put("weight", ""); // BLANK — the committer requires this
        rowMissingWeight.put("score", "40");

        ExcelParser.ParsedSheet sheet = new ExcelParser.ParsedSheet(
                List.copyOf(rowMissingWeight.keySet()), List.of(rowMissingWeight));

        ValidationResult r = validator.validateRows(sheet, def.requiredFields());

        assertThat(r.countByLevel(ImportErrorLevel.ERROR))
                .as("METHODOLOGY_FACTORS_V1.requiredFields() now includes weight/score, "
                        + "so a blank weight cell IS caught at Level-3 row validation")
                .isPositive();
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
