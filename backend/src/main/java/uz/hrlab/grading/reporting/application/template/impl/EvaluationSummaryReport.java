package uz.hrlab.grading.reporting.application.template.impl;

import com.lowagie.text.Document;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.integration.excel.ExcelWriter;
import uz.hrlab.grading.reporting.application.template.DocxBuilder;
import uz.hrlab.grading.reporting.application.template.PdfBuilder;
import uz.hrlab.grading.reporting.application.template.ReportDataPort;
import uz.hrlab.grading.reporting.application.template.ReportGenerationContext;
import uz.hrlab.grading.reporting.application.template.ReportTemplate;
import uz.hrlab.grading.reporting.application.template.ReportTemplateException;
import uz.hrlab.grading.reporting.domain.ReportFormat;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluation summary — one row per position with factor scores, total, and the
 * assigned grade. PDF and DOCX render a condensed canonical table
 * (Position | Status | Total | Grade); XLSX expands to every factor column for
 * downstream reconciliation. Order matches {@code EvaluationMatrix#factorCodes}
 * so cross-format diffs stay stable.
 *
 * <p>Salary fields are not part of EVALUATION_SUMMARY by design — this report
 * is exposed to evaluators (no salary permission). When MVP 3 introduces an
 * EVALUATION_COMPENSATION report, salary masking will live there and honor
 * {@code SALARY_VIEW}.
 */
@Component
public class EvaluationSummaryReport implements ReportTemplate {

    private final ReportDataPort data;
    private final ExcelWriter excel;

    public EvaluationSummaryReport(ReportDataPort data, ExcelWriter excel) {
        this.data = data;
        this.excel = excel;
    }

    @Override public ReportType reportType() { return ReportType.EVALUATION_SUMMARY; }

    @Override public boolean supports(ReportFormat format) {
        return format == ReportFormat.PDF
                || format == ReportFormat.DOCX
                || format == ReportFormat.XLSX;
    }

    @Override
    public void render(ReportGenerationContext ctx, OutputStream out) {
        ReportDataPort.EvaluationMatrix matrix =
                data.loadEvaluations(ctx.tenantId(), ctx.projectId());
        switch (ctx.format()) {
            case PDF -> renderPdf(ctx, out, matrix);
            case DOCX -> renderDocx(ctx, out, matrix);
            case XLSX -> renderXlsx(out, matrix);
            default -> throw new ReportTemplateException("UNSUPPORTED_FORMAT: " + ctx.format());
        }
    }

    private static final List<String> CONDENSED_HEADERS =
            List.of("Position code", "Position", "Status", "Total", "Grade");

    private void renderPdf(ReportGenerationContext ctx, OutputStream out,
                           ReportDataPort.EvaluationMatrix matrix) {
        Document doc = PdfBuilder.open(out);
        try {
            PdfBuilder.heading(doc, ctx.title());
            PdfBuilder.metaLine(doc, "Project", nz(matrix.projectName()));
            PdfBuilder.metaLine(doc, "Methodology", nz(matrix.methodologyName()));
            PdfBuilder.metaLine(doc, "Total positions",
                    String.valueOf(matrix.totalPositions()));
            PdfBuilder.metaLine(doc, "Approved", String.valueOf(matrix.approvedCount()));
            List<List<String>> tbl = new ArrayList<>();
            for (ReportDataPort.EvaluationRow r : matrix.rows()) {
                tbl.add(List.of(
                        nz(r.positionCode()),
                        nz(r.positionTitle()),
                        nz(r.status()),
                        nz(r.totalScore()),
                        nz(r.gradeCode())));
            }
            PdfBuilder.table(doc, CONDENSED_HEADERS, tbl);
            PdfBuilder.footer(doc, OffsetDateTime.now(), null);
        } finally {
            PdfBuilder.close(doc);
        }
    }

    private void renderDocx(ReportGenerationContext ctx, OutputStream out,
                            ReportDataPort.EvaluationMatrix matrix) {
        WordprocessingMLPackage pkg = DocxBuilder.create();
        DocxBuilder.heading(pkg.getMainDocumentPart(), ctx.title());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Project", nz(matrix.projectName()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Methodology",
                nz(matrix.methodologyName()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Total positions",
                String.valueOf(matrix.totalPositions()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Approved",
                String.valueOf(matrix.approvedCount()));
        List<List<String>> body = new ArrayList<>();
        for (ReportDataPort.EvaluationRow r : matrix.rows()) {
            body.add(List.of(
                    nz(r.positionCode()),
                    nz(r.positionTitle()),
                    nz(r.status()),
                    nz(r.totalScore()),
                    nz(r.gradeCode())));
        }
        DocxBuilder.table(pkg.getMainDocumentPart(), CONDENSED_HEADERS, body);
        DocxBuilder.write(pkg, out);
    }

    private void renderXlsx(OutputStream out, ReportDataPort.EvaluationMatrix matrix) {
        // XLSX expands the factor columns so reviewers can reconcile every score.
        List<String> cols = new ArrayList<>();
        cols.add("position_code");
        cols.add("position_title");
        cols.add("status");
        for (String code : matrix.factorCodes()) {
            cols.add("factor_" + code);
        }
        cols.add("total_score");
        cols.add("grade_code");

        List<Map<String, String>> dataRows = new ArrayList<>(matrix.rows().size());
        for (ReportDataPort.EvaluationRow r : matrix.rows()) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("position_code", nz(r.positionCode()));
            m.put("position_title", nz(r.positionTitle()));
            m.put("status", nz(r.status()));
            Map<String, String> scoresByCode = r.scoresByFactorCode();
            for (String code : matrix.factorCodes()) {
                String v = scoresByCode == null ? null : scoresByCode.get(code);
                m.put("factor_" + code, v == null ? "" : v);
            }
            m.put("total_score", nz(r.totalScore()));
            m.put("grade_code", nz(r.gradeCode()));
            dataRows.add(m);
        }
        byte[] bytes = excel.write("EvaluationSummary", cols, dataRows);
        try {
            out.write(bytes);
        } catch (java.io.IOException e) {
            throw new ReportTemplateException("XLSX_WRITE_FAILED", e);
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
