package uz.hrlab.grading.integration.imports.application;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.hrlab.grading.integration.excel.ExcelParser;
import uz.hrlab.grading.integration.imports.domain.ImportTemplateCode;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link ImportTemplateSamples} emits valid XLSX bytes for every
 * supported template — empty (header + DEMO banner row only) and sample
 * (header + ACME Holdings sample rows) — and that the produced bytes parse
 * back through {@link ExcelParser} (same parser used by the import worker).
 */
class ImportTemplateSamplesTest {

    private final ImportTemplateRegistry registry = new ImportTemplateRegistry();
    private final ImportTemplateSamples samples =
            new ImportTemplateSamples(registry, new ImportTemplateGuide());
    private final ExcelParser parser = new ExcelParser();

    @ParameterizedTest
    @ValueSource(strings = {
            ImportTemplateCode.ORG_STRUCTURE_V1,
            ImportTemplateCode.POSITION_CATALOG_V1,
            ImportTemplateCode.JOB_PROFILE_V1,
            ImportTemplateCode.METHODOLOGY_FACTORS_V1,
            ImportTemplateCode.GRADE_BANDS_V1,
    })
    void empty_template_has_header_plus_demo_banner_row(String templateCode) {
        byte[] xlsx = samples.generateEmpty(templateCode);
        assertThat(xlsx).isNotEmpty();
        ExcelParser.ParsedSheet parsed = parser.parse(xlsx);
        // Headers must include the canonical required columns from the registry.
        List<String> required = registry.find(templateCode).orElseThrow().requiredColumns();
        assertThat(parsed.headers()).containsAll(required);
        // Empty template = exactly one demo-banner row (no real data).
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().get(0).get(parsed.headers().get(0)))
                .contains("DEMO");
    }

    @Test
    void org_structure_sample_has_12_acme_rows_plus_banner() {
        byte[] xlsx = samples.generateSample(ImportTemplateCode.ORG_STRUCTURE_V1);
        ExcelParser.ParsedSheet parsed = parser.parse(xlsx);
        assertThat(parsed.headers())
                .contains("external_id", "name", "parent_external_id", "level", "type");
        // 1 banner + 12 ACME departments.
        assertThat(parsed.rows()).hasSize(13);
        List<String> codes = parsed.rows().stream()
                .map(r -> r.getOrDefault("external_id", ""))
                .toList();
        assertThat(codes).contains("CEO-OFFICE", "CFO-OFFICE", "SW-ENG-DEPT", "OPS-DEPT");
    }

    @Test
    void position_catalog_sample_has_15_acme_positions() {
        byte[] xlsx = samples.generateSample(ImportTemplateCode.POSITION_CATALOG_V1);
        ExcelParser.ParsedSheet parsed = parser.parse(xlsx);
        // 1 banner + 15 positions.
        assertThat(parsed.rows()).hasSize(16);
        List<String> codes = parsed.rows().stream()
                .map(r -> r.getOrDefault("external_id", ""))
                .toList();
        assertThat(codes).contains("POS-CFO", "POS-CTO", "POS-SR-SWE", "POS-OPS-MGR");
    }

    @Test
    void methodology_factors_sample_has_40_rows_8_factors_x_5_levels() {
        byte[] xlsx = samples.generateSample(ImportTemplateCode.METHODOLOGY_FACTORS_V1);
        ExcelParser.ParsedSheet parsed = parser.parse(xlsx);
        // 1 banner + 40 factor/level rows.
        assertThat(parsed.rows()).hasSize(41);
        long uniqueFactors = parsed.rows().stream()
                .map(r -> r.getOrDefault("factor_code", ""))
                .filter(s -> !s.isEmpty() && !s.startsWith("Generated"))
                .distinct()
                .count();
        assertThat(uniqueFactors).isEqualTo(8);
    }

    @Test
    void grade_bands_sample_has_14_acme_grades() {
        byte[] xlsx = samples.generateSample(ImportTemplateCode.GRADE_BANDS_V1);
        ExcelParser.ParsedSheet parsed = parser.parse(xlsx);
        // 1 banner + 14 grades.
        assertThat(parsed.rows()).hasSize(15);
        List<String> codes = parsed.rows().stream()
                .map(r -> r.getOrDefault("grade_code", ""))
                .toList();
        // grade_code is now a plain positive integer (grade_number) so the
        // shipped sample is importable by GradeBandsRowCommitter.
        assertThat(codes).contains("1", "7", "14");
    }

    @Test
    void job_profile_sample_has_5_acme_profiles() {
        byte[] xlsx = samples.generateSample(ImportTemplateCode.JOB_PROFILE_V1);
        ExcelParser.ParsedSheet parsed = parser.parse(xlsx);
        // 1 banner + 5 profiles.
        assertThat(parsed.rows()).hasSize(6);
        List<String> codes = parsed.rows().stream()
                .map(r -> r.getOrDefault("position_external_id", ""))
                .toList();
        assertThat(codes).contains("POS-CFO", "POS-CTO", "POS-HRD");
    }

    @Test
    void org_structure_sample_uses_only_valid_department_type_codes() {
        byte[] xlsx = samples.generateSample(ImportTemplateCode.ORG_STRUCTURE_V1);
        ExcelParser.ParsedSheet parsed = parser.parse(xlsx);
        java.util.Set<String> allowed = java.util.Arrays.stream(
                        uz.hrlab.grading.organization.domain.DepartmentType.values())
                .map(Enum::name).collect(java.util.stream.Collectors.toSet());
        parsed.rows().stream()
                .map(r -> r.getOrDefault("type", ""))
                .filter(t -> !t.isBlank() && !t.startsWith("Generated")) // skip banner row
                .forEach(t -> assertThat(allowed)
                        .as("sample type '" + t + "' must be a valid DepartmentType")
                        .contains(t));
    }

    @Test
    void position_catalog_sample_uses_only_valid_status_codes() {
        byte[] xlsx = samples.generateSample(ImportTemplateCode.POSITION_CATALOG_V1);
        ExcelParser.ParsedSheet parsed = parser.parse(xlsx);
        java.util.Set<String> allowed = java.util.Arrays.stream(
                        uz.hrlab.grading.position.domain.PositionStatus.values())
                .map(Enum::name).collect(java.util.stream.Collectors.toSet());
        parsed.rows().stream()
                .map(r -> r.getOrDefault("status", ""))
                .filter(s -> !s.isBlank() && !s.startsWith("Generated")) // skip banner row
                .forEach(s -> assertThat(allowed)
                        .as("sample status '" + s + "' must be a valid PositionStatus")
                        .contains(s));
    }

    @Test
    void grade_bands_sample_grade_codes_are_positive_integers() {
        byte[] xlsx = samples.generateSample(ImportTemplateCode.GRADE_BANDS_V1);
        ExcelParser.ParsedSheet parsed = parser.parse(xlsx);
        parsed.rows().stream()
                .map(r -> r.getOrDefault("grade_code", ""))
                .filter(c -> !c.isBlank() && !c.startsWith("Generated")) // skip banner row
                .forEach(c -> {
                    int n = Integer.parseInt(c); // throws if not an integer
                    assertThat(n).as("grade_code must be positive").isPositive();
                });
    }

    @Test
    void grade_bands_sample_rows_have_min_le_max() {
        byte[] xlsx = samples.generateSample(ImportTemplateCode.GRADE_BANDS_V1);
        ExcelParser.ParsedSheet parsed = parser.parse(xlsx);
        parsed.rows().stream()
                .filter(r -> {
                    String c = r.getOrDefault("grade_code", "");
                    return !c.isBlank() && !c.startsWith("Generated");
                })
                .forEach(r -> {
                    java.math.BigDecimal min = new java.math.BigDecimal(r.get("min_score").trim());
                    java.math.BigDecimal max = new java.math.BigDecimal(r.get("max_score").trim());
                    assertThat(min).as("min_score <= max_score").isLessThanOrEqualTo(max);
                });
    }

    @Test
    void methodology_sample_populates_required_weight_and_score_columns() {
        byte[] xlsx = samples.generateSample(ImportTemplateCode.METHODOLOGY_FACTORS_V1);
        ExcelParser.ParsedSheet parsed = parser.parse(xlsx);
        // Registry-required columns for METHODOLOGY_FACTORS_V1.
        assertThat(parsed.headers()).contains("weight", "score");
        parsed.rows().stream()
                .filter(r -> {
                    String fc = r.getOrDefault("factor_code", "");
                    return !fc.isBlank() && !fc.startsWith("Generated"); // skip banner row
                })
                .forEach(r -> {
                    assertThat(r.get("weight")).as("weight populated").isNotBlank();
                    assertThat(r.get("score")).as("score populated").isNotBlank();
                });
    }

    @Test
    void filename_follows_convention() {
        assertThat(samples.filename(ImportTemplateCode.ORG_STRUCTURE_V1, false))
                .isEqualTo("ORG_STRUCTURE_V1_empty.xlsx");
        assertThat(samples.filename(ImportTemplateCode.ORG_STRUCTURE_V1, true))
                .isEqualTo("ORG_STRUCTURE_V1_sample.xlsx");
    }

    @Test
    void unknown_template_throws() {
        try {
            samples.generateEmpty("UNKNOWN_FOO_V1");
        } catch (IllegalArgumentException expected) {
            // Registry throws because the templateCode is not registered.
            return;
        }
        // Fall through to fail if no exception.
        org.assertj.core.api.Assertions.fail("Expected IllegalArgumentException");
    }

    @Test
    void all_samples_contain_demo_banner() {
        for (String code : List.of(
                ImportTemplateCode.ORG_STRUCTURE_V1,
                ImportTemplateCode.POSITION_CATALOG_V1,
                ImportTemplateCode.JOB_PROFILE_V1,
                ImportTemplateCode.METHODOLOGY_FACTORS_V1,
                ImportTemplateCode.GRADE_BANDS_V1)) {
            byte[] xlsx = samples.generateSample(code);
            ExcelParser.ParsedSheet parsed = parser.parse(xlsx);
            String firstCell = parsed.rows().isEmpty() ? "" :
                    parsed.rows().get(0).get(parsed.headers().get(0));
            assertThat(firstCell).as(code).contains("DEMO");
        }
    }

    @Test
    void empty_template_headers_include_optional_columns() {
        List<String> headers = samples.headersFor(ImportTemplateCode.ORG_STRUCTURE_V1);
        assertThat(headers).contains("type", "description", "cost_center_code");
    }

    // -----------------------------------------------------------------
    // Guide sheet (sheet #2) — added per import-template guide task
    // -----------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            ImportTemplateCode.ORG_STRUCTURE_V1,
            ImportTemplateCode.POSITION_CATALOG_V1,
            ImportTemplateCode.JOB_PROFILE_V1,
            ImportTemplateCode.METHODOLOGY_FACTORS_V1,
            ImportTemplateCode.GRADE_BANDS_V1,
    })
    void empty_template_has_data_sheet_at_index0_and_guide_sheet_at_index1(String code)
            throws Exception {
        byte[] xlsx = samples.generateEmpty(code);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getNumberOfSheets()).as(code).isEqualTo(2);
            // Sheet 0 = data sheet, NOT the guide.
            assertThat(wb.getSheetAt(0).getSheetName())
                    .as(code).isNotEqualToIgnoringCase(ImportTemplateGuide.GUIDE_SHEET_NAME);
            // Sheet 1 = guide.
            assertThat(wb.getSheetAt(1).getSheetName())
                    .as(code).isEqualTo(ImportTemplateGuide.GUIDE_SHEET_NAME);
            // Data sheet row 0 still carries the canonical header row.
            org.apache.poi.ss.usermodel.Row header = wb.getSheetAt(0).getRow(0);
            assertThat(header).as(code).isNotNull();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ImportTemplateCode.ORG_STRUCTURE_V1,
            ImportTemplateCode.POSITION_CATALOG_V1,
            ImportTemplateCode.JOB_PROFILE_V1,
            ImportTemplateCode.METHODOLOGY_FACTORS_V1,
            ImportTemplateCode.GRADE_BANDS_V1,
    })
    void sample_template_has_two_sheets_and_parser_reads_data_sheet(String code) {
        byte[] xlsx = samples.generateSample(code);
        // Parser must still resolve the DATA sheet (index 0) even though a
        // guide sheet is present.
        ExcelParser.ParsedSheet parsed = parser.parse(xlsx);
        List<String> required = registry.find(code).orElseThrow().requiredColumns();
        assertThat(parsed.headers()).as(code).containsAll(required);
        assertThat(parsed.rows()).as(code).isNotEmpty();
    }

    @Test
    void guide_sheet_first_cell_is_instruction_title_not_data() throws Exception {
        byte[] xlsx = samples.generateEmpty(ImportTemplateCode.ORG_STRUCTURE_V1);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            String firstGuideCell = wb.getSheetAt(1).getRow(0).getCell(0).getStringCellValue();
            assertThat(firstGuideCell).contains("ЙЎРИҚНОМА").contains("ИНСТРУКЦИЯ");
        }
    }

    @Test
    void guide_sheet_lists_each_data_column_with_required_and_enum_rules() throws Exception {
        byte[] xlsx = samples.generateEmpty(ImportTemplateCode.ORG_STRUCTURE_V1);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            String guideText = sheetText(wb.getSheetAt(1));
            // Every data column documented.
            assertThat(guideText)
                    .contains("external_id")
                    .contains("parent_external_id")
                    .contains("type");
            // The exact DepartmentType enum codes the committer accepts.
            assertThat(guideText)
                    .contains("BRANCH").contains("DEPARTMENT")
                    .contains("DIVISION").contains("UNIT");
        }
    }

    @Test
    void filled_workbook_with_guide_sheet_round_trips_to_correct_rows() {
        // Simulate: user downloads sample (data + guide), keeps both sheets,
        // re-uploads. Parser must still pick the data sheet.
        byte[] xlsx = samples.generateSample(ImportTemplateCode.GRADE_BANDS_V1);
        ExcelParser.ParsedSheet parsed = parser.parse(xlsx);
        // 1 banner + 14 grades on the DATA sheet — guide sheet contributes 0 rows.
        assertThat(parsed.rows()).hasSize(15);
        List<String> codes = parsed.rows().stream()
                .map(r -> r.getOrDefault("grade_code", ""))
                .toList();
        assertThat(codes).contains("1", "14");
    }

    private static String sheetText(org.apache.poi.ss.usermodel.Sheet sheet) {
        StringBuilder sb = new StringBuilder();
        for (org.apache.poi.ss.usermodel.Row row : sheet) {
            for (org.apache.poi.ss.usermodel.Cell cell : row) {
                if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                    sb.append(cell.getStringCellValue()).append('\n');
                }
            }
        }
        return sb.toString();
    }
}
