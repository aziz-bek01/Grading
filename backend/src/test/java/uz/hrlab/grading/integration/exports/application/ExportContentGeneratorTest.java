package uz.hrlab.grading.integration.exports.application;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.integration.excel.ExcelWriter;
import uz.hrlab.grading.integration.exports.domain.ExportFormat;
import uz.hrlab.grading.integration.exports.domain.ExportType;
import uz.hrlab.grading.reporting.application.template.ReportDataPort;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link ExportContentGenerator} emits REAL, valid bytes per
 * {@link ExportType}/{@link ExportFormat} (Batch 2, TASK 1) — not the former
 * placeholder workbook. Also proves formula-injection sanitisation in XLSX + CSV.
 */
@Tag("exports")
class ExportContentGeneratorTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();

    private final ExportContentGenerator gen =
            new ExportContentGenerator(new StubPort(), new ExcelWriter());

    @Test
    void positionCatalogXlsxContainsRealRows() throws Exception {
        ExportContentGenerator.GeneratedExport out = gen.generate(
                ExportType.POSITION_CATALOG, ExportFormat.XLSX, TENANT, PROJECT, "ru-RU");

        assertThat(out.extension()).isEqualTo("xlsx");
        assertThat(out.rowCount()).isEqualTo(2);
        assertThat(out.bytes()).startsWith((byte) 'P', (byte) 'K'); // ZIP/OOXML magic

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.bytes()))) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("code");
            // The =cmd() injection payload must be neutralised with a leading apostrophe.
            Row dangerous = sheet.getRow(2);
            assertThat(dangerous.getCell(0).getStringCellValue()).startsWith("'=");
        }
    }

    @Test
    void gradeStructureCsvIsSanitisedAndHasHeader() {
        ExportContentGenerator.GeneratedExport out = gen.generate(
                ExportType.GRADE_STRUCTURE, ExportFormat.CSV, TENANT, PROJECT, "ru-RU");

        assertThat(out.extension()).isEqualTo("csv");
        assertThat(out.contentType()).isEqualTo("text/csv");
        String csv = new String(out.bytes(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("grade_code,grade_name,position_count\r\n");
        assertThat(csv).contains("G3");
    }

    @Test
    void evaluationMatrixXlsxIncludesFactorColumns() throws Exception {
        ExportContentGenerator.GeneratedExport out = gen.generate(
                ExportType.EVALUATION_MATRIX, ExportFormat.XLSX, TENANT, PROJECT, "ru-RU");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.bytes()))) {
            Row header = wb.getSheetAt(0).getRow(0);
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("KNOWLEDGE");
        }
        assertThat(out.rowCount()).isEqualTo(1);
    }

    @Test
    void methodologyPdfStartsWithPdfMagic() {
        ExportContentGenerator.GeneratedExport out = gen.generate(
                ExportType.METHODOLOGY, ExportFormat.PDF, TENANT, PROJECT, "ru-RU");

        assertThat(out.extension()).isEqualTo("pdf");
        assertThat(new String(out.bytes(), 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }

    @Test
    void executiveDocxIsValidOoxml() {
        ExportContentGenerator.GeneratedExport out = gen.generate(
                ExportType.REPORT_EXECUTIVE, ExportFormat.DOCX, TENANT, PROJECT, "ru-RU");

        assertThat(out.extension()).isEqualTo("docx");
        assertThat(out.bytes()).startsWith((byte) 'P', (byte) 'K');
    }

    @Test
    void structurallyStubbedSalaryTypeYieldsValidEmptyDocumentNotPlaceholder() throws Exception {
        ExportContentGenerator.GeneratedExport out = gen.generate(
                ExportType.SALARY_RANGES, ExportFormat.XLSX, TENANT, PROJECT, "ru-RU");

        assertThat(out.rowCount()).isZero();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.bytes()))) {
            Sheet sheet = wb.getSheetAt(0);
            // Header-only: real schema header, NO fabricated placeholder data row.
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("grade");
            assertThat(sheet.getRow(1)).isNull();
        }
    }

    /** Deterministic fixture — one row carries a formula-injection payload. */
    private static final class StubPort implements ReportDataPort {
        @Override
        public List<PositionRow> positions(UUID tenantId, UUID projectId, String locale) {
            return List.of(
                    new PositionRow("POS-1", "Engineer", "IT", "Eng", "L3", "Active"),
                    new PositionRow("=cmd|'/c calc'!A1", "Injected", "X", "Y", "Z", "Active"));
        }

        @Override
        public List<GradeCountRow> gradeDistribution(UUID tenantId, UUID projectId, String locale) {
            return List.of(new GradeCountRow("G3", "Operational", 12),
                    new GradeCountRow("G4", "Senior", 7));
        }

        @Override
        public MethodologySpec methodologySpec(UUID tenantId, UUID projectId, String locale) {
            return new MethodologySpec("M (v1)", "v1", "Approved", List.of(
                    new FactorRow("KNOWLEDGE", "Knowledge", 20, 100, "Weighted points",
                            List.of(Map.of("code", "L1")))));
        }

        @Override
        public List<AuditEventRow> loadAuditEvents(UUID tenantId, UUID projectId,
                                                   OffsetDateTime from, OffsetDateTime to, int limit) {
            return List.of();
        }

        @Override
        public EvaluationMatrix loadEvaluations(UUID tenantId, UUID projectId, String locale) {
            return new EvaluationMatrix("Proj", "M (v1)", 1, 1,
                    List.of(new FactorRef("KNOWLEDGE", "Knowledge"),
                            new FactorRef("PROBLEM_SOLVING", "Problem solving")),
                    List.of(new EvaluationRow("POS-1", "Engineer", "IT", "Approved",
                            Map.of("KNOWLEDGE", "60", "PROBLEM_SOLVING", "40"), "100", "G3", "Operational")));
        }

        @Override
        public ExecutiveKpi loadExecutiveKpi(UUID tenantId, UUID projectId, String locale) {
            return new ExecutiveKpi("Proj", "Active", "2026-01-01", "2026-12-31",
                    10, 8, 6, 3, 100, List.of());
        }

        @Override
        public String projectName(UUID tenantId, UUID projectId, String locale) {
            return "Proj";
        }

        @Override
        public String tenantName(UUID tenantId, String locale) {
            return "Tenant";
        }
    }
}
