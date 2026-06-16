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
import uz.hrlab.grading.reporting.domain.ReportType;

import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bar chart of positions per grade (architecture §17). Fully implemented for
 * PDF, DOCX, XLSX.
 */
@Component
public class GradeDistributionReport
        extends AbstractReportTemplate<List<ReportDataPort.GradeCountRow>> {

    private final ReportDataPort data;
    private final ExcelWriter excel;

    public GradeDistributionReport(ReportDataPort data, ExcelWriter excel) {
        this.data = data;
        this.excel = excel;
    }

    @Override public ReportType reportType() { return ReportType.GRADE_DISTRIBUTION; }

    @Override
    protected List<ReportDataPort.GradeCountRow> loadData(ReportGenerationContext ctx) {
        return data.gradeDistribution(ctx.tenantId(), ctx.projectId());
    }

    @Override
    protected void renderPdf(ReportGenerationContext ctx, OutputStream out,
                             List<ReportDataPort.GradeCountRow> rows) {
        Document doc = PdfBuilder.open(out);
        try {
            PdfBuilder.heading(doc, ctx.title());
            PdfBuilder.metaLine(doc, "Project", ctx.projectId().toString());
            PdfBuilder.metaLine(doc, "Locale", ctx.locale());
            PdfBuilder.subheading(doc, "Distribution");
            PdfBuilder.barChart(doc,
                    rows.stream().map(ReportDataPort.GradeCountRow::gradeCode).toList(),
                    rows.stream().map(ReportDataPort.GradeCountRow::positionCount).toList());
            PdfBuilder.subheading(doc, "Detail");
            List<List<String>> table = rows.stream()
                    .map(r -> List.of(r.gradeCode(),
                            r.gradeName() == null ? "" : r.gradeName(),
                            String.valueOf(r.positionCount())))
                    .toList();
            PdfBuilder.table(doc, List.of("Grade", "Name", "Positions"), table);
            PdfBuilder.footer(doc, OffsetDateTime.now(), null);
        } finally {
            PdfBuilder.close(doc);
        }
    }

    @Override
    protected void renderDocx(ReportGenerationContext ctx, OutputStream out,
                              List<ReportDataPort.GradeCountRow> rows) {
        WordprocessingMLPackage pkg = DocxBuilder.create();
        DocxBuilder.heading(pkg.getMainDocumentPart(), ctx.title());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Project", ctx.projectId().toString());
        DocxBuilder.subheading(pkg.getMainDocumentPart(), "Detail");
        List<List<String>> table = new ArrayList<>();
        table.add(List.of("Grade", "Name", "Positions"));
        for (ReportDataPort.GradeCountRow r : rows) {
            table.add(List.of(r.gradeCode(),
                    r.gradeName() == null ? "" : r.gradeName(),
                    String.valueOf(r.positionCount())));
        }
        DocxBuilder.table(pkg.getMainDocumentPart(),
                table.get(0), table.subList(1, table.size()));
        DocxBuilder.write(pkg, out);
    }

    @Override
    protected void renderXlsx(ReportGenerationContext ctx, OutputStream out,
                              List<ReportDataPort.GradeCountRow> rows) {
        List<Map<String, String>> dataRows = new ArrayList<>();
        for (ReportDataPort.GradeCountRow r : rows) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("grade_code", r.gradeCode());
            m.put("grade_name", r.gradeName() == null ? "" : r.gradeName());
            m.put("positions", String.valueOf(r.positionCount()));
            dataRows.add(m);
        }
        writeXlsx(out, excel.write("GradeDistribution",
                List.of("grade_code", "grade_name", "positions"), dataRows));
    }
}
