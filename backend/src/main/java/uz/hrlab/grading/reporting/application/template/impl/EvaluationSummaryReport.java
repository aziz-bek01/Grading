package uz.hrlab.grading.reporting.application.template.impl;

import com.lowagie.text.Document;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.integration.excel.ExcelWriter;
import uz.hrlab.grading.reporting.application.template.AbstractReportTemplate;
import uz.hrlab.grading.reporting.application.template.DocxBuilder;
import uz.hrlab.grading.reporting.application.template.PdfBuilder;
import uz.hrlab.grading.reporting.application.template.ReportDataPort;
import uz.hrlab.grading.reporting.application.template.ReportGenerationContext;
import uz.hrlab.grading.reporting.application.template.ReportLabels;
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
 * (Position | Department | Status | Total | Grade); XLSX expands to every factor
 * column for downstream reconciliation. Column order matches {@code
 * EvaluationMatrix#factors} so cross-format diffs stay stable.
 *
 * <p>WIRE NAMING: the XLSX factor-column DATA-ROW lookup key stays the factor
 * code; only the human HEADER label is localized ({@code name + " (" + code +
 * ")"}). Status, grade, methodology, and department are localized in the port.
 *
 * <p>Salary fields are not part of EVALUATION_SUMMARY by design — this report
 * is exposed to evaluators (no salary permission). When MVP 3 introduces an
 * EVALUATION_COMPENSATION report, salary masking will live there and honor
 * {@code SALARY_VIEW}.
 */
@Component
public class EvaluationSummaryReport
        extends AbstractReportTemplate<ReportDataPort.EvaluationMatrix> {

    private final ReportDataPort data;
    private final ExcelWriter excel;

    public EvaluationSummaryReport(ReportDataPort data, ExcelWriter excel) {
        this.data = data;
        this.excel = excel;
    }

    @Override public ReportType reportType() { return ReportType.EVALUATION_SUMMARY; }

    @Override
    protected ReportDataPort.EvaluationMatrix loadData(ReportGenerationContext ctx) {
        return data.loadEvaluations(ctx.tenantId(), ctx.projectId(), ctx.locale());
    }

    private static List<String> condensedHeaders(String locale) {
        return List.of(
                ReportLabels.label("col.positionCode", locale),
                ReportLabels.label("col.position", locale),
                ReportLabels.label("col.department", locale),
                ReportLabels.label("col.status", locale),
                ReportLabels.label("col.total", locale),
                ReportLabels.label("col.grade", locale));
    }

    @Override
    protected void renderPdf(ReportGenerationContext ctx, OutputStream out,
                             ReportDataPort.EvaluationMatrix matrix) {
        String locale = ctx.locale();
        Document doc = PdfBuilder.open(out);
        try {
            PdfBuilder.heading(doc, ctx.title());
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.project", locale), nz(matrix.projectName()));
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.methodology", locale),
                    nz(matrix.methodologyName()));
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.totalPositions", locale),
                    String.valueOf(matrix.totalPositions()));
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.approved", locale),
                    String.valueOf(matrix.approvedCount()));
            if (matrix.rows().isEmpty()) {
                PdfBuilder.paragraph(doc, ReportLabels.label("empty.evaluations", locale));
            } else {
                List<List<String>> tbl = new ArrayList<>();
                for (ReportDataPort.EvaluationRow r : matrix.rows()) {
                    tbl.add(List.of(
                            nz(r.positionCode()),
                            nz(r.positionTitle()),
                            nz(r.departmentName()),
                            nz(r.status()),
                            nz(r.totalScore()),
                            nz(r.gradeName())));
                }
                PdfBuilder.table(doc, condensedHeaders(locale), tbl);
            }
            PdfBuilder.footer(doc, OffsetDateTime.now(), null);
        } finally {
            PdfBuilder.close(doc);
        }
    }

    @Override
    protected void renderDocx(ReportGenerationContext ctx, OutputStream out,
                              ReportDataPort.EvaluationMatrix matrix) {
        String locale = ctx.locale();
        WordprocessingMLPackage pkg = DocxBuilder.create();
        DocxBuilder.heading(pkg.getMainDocumentPart(), ctx.title());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.project", locale), nz(matrix.projectName()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.methodology", locale), nz(matrix.methodologyName()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.totalPositions", locale),
                String.valueOf(matrix.totalPositions()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.approved", locale),
                String.valueOf(matrix.approvedCount()));
        if (matrix.rows().isEmpty()) {
            DocxBuilder.paragraph(pkg.getMainDocumentPart(),
                    ReportLabels.label("empty.evaluations", locale));
        } else {
            List<List<String>> body = new ArrayList<>();
            for (ReportDataPort.EvaluationRow r : matrix.rows()) {
                body.add(List.of(
                        nz(r.positionCode()),
                        nz(r.positionTitle()),
                        nz(r.departmentName()),
                        nz(r.status()),
                        nz(r.totalScore()),
                        nz(r.gradeName())));
            }
            DocxBuilder.table(pkg.getMainDocumentPart(), condensedHeaders(locale), body);
        }
        DocxBuilder.write(pkg, out);
    }

    @Override
    protected void renderXlsx(ReportGenerationContext ctx, OutputStream out,
                              ReportDataPort.EvaluationMatrix matrix) {
        String locale = ctx.locale();
        // XLSX expands the factor columns so reviewers can reconcile every score.
        // dataKeys stay machine-stable (factor code keyed); displayHeaders are the
        // localized human labels ("name (CODE)" for factors).
        List<String> displayHeaders = new ArrayList<>();
        List<String> dataKeys = new ArrayList<>();
        displayHeaders.add(ReportLabels.label("col.positionCode", locale));
        dataKeys.add("position_code");
        displayHeaders.add(ReportLabels.label("col.position", locale));
        dataKeys.add("position_title");
        displayHeaders.add(ReportLabels.label("col.department", locale));
        dataKeys.add("department");
        displayHeaders.add(ReportLabels.label("col.status", locale));
        dataKeys.add("status");
        for (ReportDataPort.FactorRef f : matrix.factors()) {
            displayHeaders.add(nz(f.name()) + " (" + f.code() + ")");
            dataKeys.add("factor_" + f.code());
        }
        displayHeaders.add(ReportLabels.label("col.total", locale));
        dataKeys.add("total_score");
        displayHeaders.add(ReportLabels.label("col.grade", locale));
        dataKeys.add("grade_code");
        displayHeaders.add(ReportLabels.label("col.gradeName", locale));
        dataKeys.add("grade_name");

        List<Map<String, String>> dataRows = new ArrayList<>(matrix.rows().size());
        for (ReportDataPort.EvaluationRow r : matrix.rows()) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("position_code", nz(r.positionCode()));
            m.put("position_title", nz(r.positionTitle()));
            m.put("department", nz(r.departmentName()));
            m.put("status", nz(r.status()));
            Map<String, String> scoresByCode = r.scoresByFactorCode();
            for (ReportDataPort.FactorRef f : matrix.factors()) {
                String v = scoresByCode == null ? null : scoresByCode.get(f.code());
                m.put("factor_" + f.code(), v == null ? "" : v);
            }
            m.put("total_score", nz(r.totalScore()));
            m.put("grade_code", nz(r.gradeCode()));
            m.put("grade_name", nz(r.gradeName()));
            dataRows.add(m);
        }
        writeXlsx(out, excel.write("EvaluationSummary", displayHeaders, dataKeys, dataRows));
    }
}
