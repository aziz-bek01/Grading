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
 * Full position list with metadata — code, title, department, family, level,
 * status. PDF + DOCX + XLSX implemented. Department name and status are
 * localized in the port; meta lines show the project NAME (not the UUID).
 */
@Component
public class PositionCatalogReport
        extends AbstractReportTemplate<PositionCatalogReport.Model> {

    private final ReportDataPort data;
    private final ExcelWriter excel;

    public PositionCatalogReport(ReportDataPort data, ExcelWriter excel) {
        this.data = data;
        this.excel = excel;
    }

    @Override public ReportType reportType() { return ReportType.POSITION_CATALOG; }

    /** Carries the rows plus the resolved project name so meta lines avoid the UUID. */
    record Model(String projectName, List<ReportDataPort.PositionRow> rows) { }

    @Override
    protected Model loadData(ReportGenerationContext ctx) {
        return new Model(
                data.projectName(ctx.tenantId(), ctx.projectId(), ctx.locale()),
                data.positions(ctx.tenantId(), ctx.projectId(), ctx.locale()));
    }

    private static List<String> headers(String locale) {
        return List.of(
                ReportLabels.label("col.code", locale),
                ReportLabels.label("col.title", locale),
                ReportLabels.label("col.department", locale),
                ReportLabels.label("col.jobFamily", locale),
                ReportLabels.label("col.jobLevel", locale),
                ReportLabels.label("col.status", locale));
    }

    @Override
    protected void renderPdf(ReportGenerationContext ctx, OutputStream out, Model model) {
        String locale = ctx.locale();
        List<ReportDataPort.PositionRow> rows = model.rows();
        Document doc = PdfBuilder.open(out);
        try {
            PdfBuilder.heading(doc, ctx.title());
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.project", locale), nz(model.projectName()));
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.locale", locale), locale);
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.positionCount", locale),
                    String.valueOf(rows.size()));
            if (rows.isEmpty()) {
                PdfBuilder.paragraph(doc, ReportLabels.label("empty.positions", locale));
            } else {
                List<List<String>> tbl = rows.stream()
                        .map(r -> List.of(
                                nz(r.code()), nz(r.title()), nz(r.departmentName()),
                                nz(r.jobFamily()), nz(r.jobLevel()), nz(r.status())))
                        .toList();
                PdfBuilder.table(doc, headers(locale), tbl);
            }
            PdfBuilder.footer(doc, OffsetDateTime.now(), null);
        } finally {
            PdfBuilder.close(doc);
        }
    }

    @Override
    protected void renderDocx(ReportGenerationContext ctx, OutputStream out, Model model) {
        String locale = ctx.locale();
        List<ReportDataPort.PositionRow> rows = model.rows();
        WordprocessingMLPackage pkg = DocxBuilder.create();
        DocxBuilder.heading(pkg.getMainDocumentPart(), ctx.title());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.project", locale), nz(model.projectName()));
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.positionCount", locale), String.valueOf(rows.size()));
        if (rows.isEmpty()) {
            DocxBuilder.paragraph(pkg.getMainDocumentPart(),
                    ReportLabels.label("empty.positions", locale));
        } else {
            List<List<String>> body = new ArrayList<>();
            for (ReportDataPort.PositionRow r : rows) {
                body.add(List.of(nz(r.code()), nz(r.title()), nz(r.departmentName()),
                        nz(r.jobFamily()), nz(r.jobLevel()), nz(r.status())));
            }
            DocxBuilder.table(pkg.getMainDocumentPart(), headers(locale), body);
        }
        DocxBuilder.write(pkg, out);
    }

    @Override
    protected void renderXlsx(ReportGenerationContext ctx, OutputStream out, Model model) {
        String locale = ctx.locale();
        List<String> displayHeaders = headers(locale);
        List<String> dataKeys = List.of("code", "title", "department", "job_family", "job_level", "status");
        List<Map<String, String>> dataRows = new ArrayList<>();
        for (ReportDataPort.PositionRow r : model.rows()) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("code", nz(r.code()));
            m.put("title", nz(r.title()));
            m.put("department", nz(r.departmentName()));
            m.put("job_family", nz(r.jobFamily()));
            m.put("job_level", nz(r.jobLevel()));
            m.put("status", nz(r.status()));
            dataRows.add(m);
        }
        writeXlsx(out, excel.write("PositionCatalog", displayHeaders, dataKeys, dataRows));
    }
}
