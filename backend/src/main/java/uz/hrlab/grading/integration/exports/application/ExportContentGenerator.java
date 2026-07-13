package uz.hrlab.grading.integration.exports.application;

import com.lowagie.text.Document;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.integration.excel.ExcelWriter;
import uz.hrlab.grading.integration.excel.SafeCellWriter;
import uz.hrlab.grading.integration.exports.domain.ExportFormat;
import uz.hrlab.grading.integration.exports.domain.ExportType;
import uz.hrlab.grading.integration.docs.DocxBuilder;
import uz.hrlab.grading.integration.docs.PdfBuilder;
import uz.hrlab.grading.integration.reportdata.ReportDataPort;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Produces REAL, tenant-scoped content for each {@link ExportType} (Batch 2,
 * TASK 1). Replaces the former placeholder workbook written by
 * {@code ExportGenerationJob}.
 *
 * <p>Data is sourced exclusively through the tenant + project scoped
 * {@link ReportDataPort} (the same read-only port the report module uses) — the
 * generator never touches a repository directly and never receives a tenant id
 * from anywhere but the worker's validated security context.
 *
 * <p>Every user-originated string written into an XLSX cell goes through
 * {@link ExcelWriter} (which routes through {@link SafeCellWriter}); every CSV
 * field is sanitised with {@link SafeCellWriter#sanitize(String)} so a leading
 * {@code = + - @} (or tab/CR/LF) cannot become an executable spreadsheet
 * formula (integration-blueprint §13.1).
 *
 * <p>Coverage (Batch 2):
 * <ul>
 *   <li>Fully wired (real rows): {@code POSITION_CATALOG}, {@code GRADE_STRUCTURE},
 *       {@code GRADE_PYRAMID}, {@code EVALUATION_MATRIX}, {@code METHODOLOGY},
 *       {@code REPORT_EXECUTIVE}.</li>
 *   <li>Structurally-stubbed (correct, empty-but-valid document — no salary data
 *       source exists yet; ships in a later salary batch): {@code JOB_PROFILES},
 *       {@code SALARY_RANGES}, {@code RED_GREEN_CIRCLE},
 *       {@code COMPENSATION_SCENARIOS}. These never emit a fake placeholder row —
 *       they emit a valid header-only document and {@code rowCount=0}.</li>
 * </ul>
 */
@Component
public class ExportContentGenerator {

    private final ReportDataPort data;
    private final ExcelWriter excel;

    public ExportContentGenerator(ReportDataPort data, ExcelWriter excel) {
        this.data = data;
        this.excel = excel;
    }

    /** Generated artifact bytes + provenance the worker persists onto the job row. */
    public record GeneratedExport(byte[] bytes, int rowCount, String extension, String contentType) { }

    /**
     * Build the export artifact. {@code tenantId}/{@code projectId} are passed by
     * the worker from its validated security context — never from user input.
     */
    public GeneratedExport generate(ExportType type, ExportFormat format,
                                    UUID tenantId, UUID projectId, String locale) {
        Table table = buildTable(type, tenantId, projectId, locale);
        return switch (format) {
            case XLSX -> new GeneratedExport(
                    xlsx(table), table.rows().size(), "xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case CSV -> new GeneratedExport(
                    csv(table), table.rows().size(), "csv", "text/csv");
            case PDF -> new GeneratedExport(
                    pdf(type, table), table.rows().size(), "pdf", "application/pdf");
            case DOCX -> new GeneratedExport(
                    docx(type, table), table.rows().size(), "docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        };
    }

    // --- tabular model (one sheet per export) -------------------------------

    private record Table(String sheetName, List<String> headers, List<List<String>> rows) { }

    private Table buildTable(ExportType type, UUID tenantId, UUID projectId, String locale) {
        return switch (type) {
            case POSITION_CATALOG -> positionCatalog(tenantId, projectId, locale);
            case GRADE_STRUCTURE, GRADE_PYRAMID -> gradeDistribution(tenantId, projectId, locale);
            case EVALUATION_MATRIX -> evaluationMatrix(tenantId, projectId, locale);
            case METHODOLOGY -> methodology(tenantId, projectId, locale);
            case REPORT_EXECUTIVE -> executive(tenantId, projectId, locale);
            // Structurally-stubbed: no tenant-scoped salary/job-profile data source
            // exists yet. Emit a valid header-only document (NOT a fake placeholder).
            case JOB_PROFILES -> new Table("JobProfiles",
                    List.of("code", "title", "status"), List.of());
            case SALARY_RANGES -> new Table("SalaryRanges",
                    List.of("grade", "min", "mid", "max", "currency"), List.of());
            case RED_GREEN_CIRCLE -> new Table("RedGreenCircle",
                    List.of("position_code", "current_pay", "range_position", "flag"), List.of());
            case COMPENSATION_SCENARIOS -> new Table("CompensationScenarios",
                    List.of("scenario", "grade", "budget_impact"), List.of());
        };
    }

    private Table positionCatalog(UUID tenantId, UUID projectId, String locale) {
        List<ReportDataPort.PositionRow> src = data.positions(tenantId, projectId, locale);
        List<List<String>> rows = new ArrayList<>(src.size());
        for (ReportDataPort.PositionRow r : src) {
            rows.add(List.of(nz(r.code()), nz(r.title()), nz(r.departmentName()),
                    nz(r.jobFamily()), nz(r.jobLevel()), nz(r.status())));
        }
        return new Table("PositionCatalog",
                List.of("code", "title", "department", "job_family", "job_level", "status"), rows);
    }

    private Table gradeDistribution(UUID tenantId, UUID projectId, String locale) {
        List<ReportDataPort.GradeCountRow> src = data.gradeDistribution(tenantId, projectId, locale);
        List<List<String>> rows = new ArrayList<>(src.size());
        for (ReportDataPort.GradeCountRow r : src) {
            rows.add(List.of(nz(r.gradeCode()), nz(r.gradeName()), String.valueOf(r.positionCount())));
        }
        return new Table("GradeStructure",
                List.of("grade_code", "grade_name", "position_count"), rows);
    }

    private Table evaluationMatrix(UUID tenantId, UUID projectId, String locale) {
        // Exports are unfiltered (the structured report filter is EVALUATION_SUMMARY
        // report-only, out of scope for the export path) — pass the no-op filter.
        ReportDataPort.EvaluationMatrix m = data.loadEvaluations(tenantId, projectId, locale,
                uz.hrlab.grading.integration.reportdata.EvaluationReportFilter.none());
        // Header label keeps the factor code for export-sheet stability; the
        // score lookup is keyed by code.
        List<String> headers = new ArrayList<>();
        headers.add("position_code");
        headers.add("position_title");
        headers.add("status");
        for (ReportDataPort.FactorRef f : m.factors()) {
            headers.add(f.code());
        }
        headers.add("total_score");
        headers.add("grade");
        List<List<String>> rows = new ArrayList<>(m.rows().size());
        for (ReportDataPort.EvaluationRow r : m.rows()) {
            List<String> row = new ArrayList<>(headers.size());
            row.add(nz(r.positionCode()));
            row.add(nz(r.positionTitle()));
            row.add(nz(r.status()));
            for (ReportDataPort.FactorRef f : m.factors()) {
                row.add(nz(r.scoresByFactorCode().get(f.code())));
            }
            row.add(nz(r.totalScore()));
            row.add(nz(r.gradeCode()));
            rows.add(row);
        }
        return new Table("EvaluationMatrix", headers, rows);
    }

    private Table methodology(UUID tenantId, UUID projectId, String locale) {
        ReportDataPort.MethodologySpec spec = data.methodologySpec(tenantId, projectId, locale);
        List<List<String>> rows = new ArrayList<>();
        for (ReportDataPort.FactorRow f : spec.factors()) {
            rows.add(List.of(nz(f.code()), nz(f.title()), String.valueOf(f.weight()),
                    String.valueOf(f.maxPoints()), nz(f.scoringMode()),
                    String.valueOf(f.levels() == null ? 0 : f.levels().size())));
        }
        return new Table("Methodology",
                List.of("factor_code", "factor_title", "weight", "max_points",
                        "scoring_mode", "level_count"), rows);
    }

    private Table executive(UUID tenantId, UUID projectId, String locale) {
        // Exports stay unfiltered (out of scope) — pass the no-op filter, exactly
        // as this generator already does for loadEvaluations.
        ReportDataPort.ExecutiveKpi kpi = data.loadExecutiveKpi(tenantId, projectId, locale,
                uz.hrlab.grading.integration.reportdata.EvaluationReportFilter.none());
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Project", nz(kpi.projectName())));
        rows.add(List.of("Status", nz(kpi.projectStatus())));
        rows.add(List.of("Positions", String.valueOf(kpi.positionCount())));
        rows.add(List.of("Evaluated", String.valueOf(kpi.evaluatedCount())));
        rows.add(List.of("Approved evaluations", String.valueOf(kpi.approvedEvaluationCount())));
        rows.add(List.of("Distinct grades", String.valueOf(kpi.gradeCount())));
        return new Table("ExecutiveSummary", List.of("metric", "value"), rows);
    }

    // --- format renderers ---------------------------------------------------

    private byte[] xlsx(Table t) {
        List<Map<String, String>> dataRows = new ArrayList<>(t.rows().size());
        for (List<String> row : t.rows()) {
            Map<String, String> m = new LinkedHashMap<>();
            for (int c = 0; c < t.headers().size(); c++) {
                m.put(t.headers().get(c), c < row.size() ? row.get(c) : "");
            }
            dataRows.add(m);
        }
        // ExcelWriter routes every value through SafeCellWriter (ArchUnit-enforced).
        return excel.write(t.sheetName(), t.headers(), dataRows);
    }

    private byte[] csv(Table t) {
        StringBuilder sb = new StringBuilder();
        sb.append(csvLine(t.headers()));
        for (List<String> row : t.rows()) {
            sb.append(csvLine(row));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** RFC-4180 quoting + formula-injection sanitisation per field. */
    private static String csvLine(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(csvField(fields.get(i)));
        }
        sb.append("\r\n");
        return sb.toString();
    }

    private static String csvField(String raw) {
        String safe = SafeCellWriter.sanitize(raw == null ? "" : raw);
        boolean mustQuote = safe.contains(",") || safe.contains("\"")
                || safe.contains("\n") || safe.contains("\r");
        if (!mustQuote) return safe;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private byte[] pdf(ExportType type, Table t) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = PdfBuilder.open(out);
        try {
            PdfBuilder.heading(doc, title(type));
            PdfBuilder.metaLine(doc, "Row count", String.valueOf(t.rows().size()));
            PdfBuilder.table(doc, t.headers(), t.rows());
            PdfBuilder.footer(doc, OffsetDateTime.now(), null);
        } finally {
            PdfBuilder.close(doc);
        }
        return out.toByteArray();
    }

    private byte[] docx(ExportType type, Table t) {
        WordprocessingMLPackage pkg = DocxBuilder.create();
        DocxBuilder.heading(pkg.getMainDocumentPart(), title(type));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Row count", String.valueOf(t.rows().size()));
        DocxBuilder.table(pkg.getMainDocumentPart(), t.headers(), t.rows());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DocxBuilder.write(pkg, out);
        return out.toByteArray();
    }

    private static String title(ExportType type) {
        return switch (type) {
            case POSITION_CATALOG -> "Position catalog";
            case GRADE_STRUCTURE -> "Grade structure";
            case GRADE_PYRAMID -> "Grade pyramid";
            case EVALUATION_MATRIX -> "Evaluation matrix";
            case METHODOLOGY -> "Methodology specification";
            case REPORT_EXECUTIVE -> "Executive summary";
            case JOB_PROFILES -> "Job profiles";
            case SALARY_RANGES -> "Salary ranges";
            case RED_GREEN_CIRCLE -> "Red / green circle";
            case COMPENSATION_SCENARIOS -> "Compensation scenarios";
        };
    }

    private static String nz(String s) { return s == null ? "" : s; }

    @SuppressWarnings("unused")
    private static byte[] empty() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
