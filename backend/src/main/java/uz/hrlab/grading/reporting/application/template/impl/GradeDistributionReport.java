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
 * Bar chart of positions per grade (architecture §17). Fully implemented for
 * PDF, DOCX, XLSX. The bar chart X-axis uses the localized grade NAME (not the
 * synthetic {@code G<n>} code); meta lines show the project NAME (not the UUID).
 */
@Component
public class GradeDistributionReport
        extends AbstractReportTemplate<GradeDistributionReport.Model> {

    private final ReportDataPort data;
    private final ExcelWriter excel;

    public GradeDistributionReport(ReportDataPort data, ExcelWriter excel) {
        this.data = data;
        this.excel = excel;
    }

    @Override public ReportType reportType() { return ReportType.GRADE_DISTRIBUTION; }

    /** Carries the rows plus the resolved project name so meta lines avoid the UUID. */
    record Model(String projectName, List<ReportDataPort.GradeCountRow> rows) { }

    @Override
    protected Model loadData(ReportGenerationContext ctx) {
        return new Model(
                data.projectName(ctx.tenantId(), ctx.projectId(), ctx.locale()),
                data.gradeDistribution(ctx.tenantId(), ctx.projectId(), ctx.locale()));
    }

    private static List<String> detailHeaders(String locale) {
        return List.of(
                ReportLabels.label("col.grade", locale),
                ReportLabels.label("col.gradeName", locale),
                ReportLabels.label("col.positions", locale));
    }

    @Override
    protected void renderPdf(ReportGenerationContext ctx, OutputStream out, Model model) {
        String locale = ctx.locale();
        List<ReportDataPort.GradeCountRow> rows = model.rows();
        Document doc = PdfBuilder.open(out);
        try {
            PdfBuilder.heading(doc, ctx.title());
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.project", locale), nz(model.projectName()));
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.locale", locale), locale);
            if (rows.isEmpty()) {
                PdfBuilder.paragraph(doc, ReportLabels.label("empty.grades", locale));
                PdfBuilder.footer(doc, OffsetDateTime.now(), null);
                return;
            }
            PdfBuilder.subheading(doc, ReportLabels.label("section.distribution", locale));
            PdfBuilder.barChart(doc,
                    rows.stream().map(r -> r.gradeName() == null || r.gradeName().isBlank()
                            ? r.gradeCode() : r.gradeName()).toList(),
                    rows.stream().map(ReportDataPort.GradeCountRow::positionCount).toList());
            PdfBuilder.subheading(doc, ReportLabels.label("section.detail", locale));
            List<List<String>> table = rows.stream()
                    .map(r -> List.of(r.gradeCode(),
                            r.gradeName() == null ? "" : r.gradeName(),
                            String.valueOf(r.positionCount())))
                    .toList();
            PdfBuilder.table(doc, detailHeaders(locale), table);
            PdfBuilder.footer(doc, OffsetDateTime.now(), null);
        } finally {
            PdfBuilder.close(doc);
        }
    }

    @Override
    protected void renderDocx(ReportGenerationContext ctx, OutputStream out, Model model) {
        String locale = ctx.locale();
        List<ReportDataPort.GradeCountRow> rows = model.rows();
        WordprocessingMLPackage pkg = DocxBuilder.create();
        DocxBuilder.heading(pkg.getMainDocumentPart(), ctx.title());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.project", locale), nz(model.projectName()));
        if (rows.isEmpty()) {
            DocxBuilder.paragraph(pkg.getMainDocumentPart(),
                    ReportLabels.label("empty.grades", locale));
            DocxBuilder.write(pkg, out);
            return;
        }
        DocxBuilder.subheading(pkg.getMainDocumentPart(), ReportLabels.label("section.detail", locale));
        List<List<String>> body = new ArrayList<>();
        for (ReportDataPort.GradeCountRow r : rows) {
            body.add(List.of(r.gradeCode(),
                    r.gradeName() == null ? "" : r.gradeName(),
                    String.valueOf(r.positionCount())));
        }
        DocxBuilder.table(pkg.getMainDocumentPart(), detailHeaders(locale), body);
        DocxBuilder.write(pkg, out);
    }

    @Override
    protected void renderXlsx(ReportGenerationContext ctx, OutputStream out, Model model) {
        String locale = ctx.locale();
        List<String> displayHeaders = detailHeaders(locale);
        List<String> dataKeys = List.of("grade_code", "grade_name", "positions");
        List<Map<String, String>> dataRows = new ArrayList<>();
        for (ReportDataPort.GradeCountRow r : model.rows()) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("grade_code", r.gradeCode());
            m.put("grade_name", r.gradeName() == null ? "" : r.gradeName());
            m.put("positions", String.valueOf(r.positionCount()));
            dataRows.add(m);
        }
        writeXlsx(out, excel.write("GradeDistribution", displayHeaders, dataKeys, dataRows));
    }
}
