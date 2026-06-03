package uz.hrlab.grading.integration.imports.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.hrlab.grading.integration.excel.ExcelParser;
import uz.hrlab.grading.integration.imports.domain.ImportTemplateCode;

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
    private final ImportTemplateSamples samples = new ImportTemplateSamples(registry);
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
        assertThat(codes).contains("G1", "G7", "G14");
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
}
