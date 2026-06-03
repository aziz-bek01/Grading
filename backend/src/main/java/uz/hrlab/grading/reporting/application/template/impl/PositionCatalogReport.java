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
 * Full position list with metadata — code, title, department, family, level,
 * status. PDF + DOCX + XLSX implemented.
 */
@Component
public class PositionCatalogReport implements ReportTemplate {

    private final ReportDataPort data;
    private final ExcelWriter excel;

    public PositionCatalogReport(ReportDataPort data, ExcelWriter excel) {
        this.data = data;
        this.excel = excel;
    }

    @Override public ReportType reportType() { return ReportType.POSITION_CATALOG; }

    @Override public boolean supports(ReportFormat format) {
        return format == ReportFormat.PDF
                || format == ReportFormat.DOCX
                || format == ReportFormat.XLSX;
    }

    @Override
    public void render(ReportGenerationContext ctx, OutputStream out) {
        List<ReportDataPort.PositionRow> rows = data.positions(ctx.tenantId(), ctx.projectId());
        switch (ctx.format()) {
            case PDF -> renderPdf(ctx, out, rows);
            case DOCX -> renderDocx(ctx, out, rows);
            case XLSX -> renderXlsx(out, rows);
            default -> throw new ReportTemplateException("UNSUPPORTED_FORMAT: " + ctx.format());
        }
    }

    private static final List<String> HEADERS =
            List.of("Code", "Title", "Department", "Job family", "Job level", "Status");

    private void renderPdf(ReportGenerationContext ctx, OutputStream out,
                           List<ReportDataPort.PositionRow> rows) {
        Document doc = PdfBuilder.open(out);
        try {
            PdfBuilder.heading(doc, ctx.title());
            PdfBuilder.metaLine(doc, "Project", ctx.projectId().toString());
            PdfBuilder.metaLine(doc, "Locale", ctx.locale());
            PdfBuilder.metaLine(doc, "Position count", String.valueOf(rows.size()));
            List<List<String>> tbl = rows.stream()
                    .map(r -> List.of(
                            nz(r.code()), nz(r.title()), nz(r.departmentName()),
                            nz(r.jobFamily()), nz(r.jobLevel()), nz(r.status())))
                    .toList();
            PdfBuilder.table(doc, HEADERS, tbl);
            PdfBuilder.footer(doc, OffsetDateTime.now(), null);
        } finally {
            PdfBuilder.close(doc);
        }
    }

    private void renderDocx(ReportGenerationContext ctx, OutputStream out,
                            List<ReportDataPort.PositionRow> rows) {
        WordprocessingMLPackage pkg = DocxBuilder.create();
        DocxBuilder.heading(pkg.getMainDocumentPart(), ctx.title());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Project", ctx.projectId().toString());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Position count", String.valueOf(rows.size()));
        List<List<String>> body = new ArrayList<>();
        for (ReportDataPort.PositionRow r : rows) {
            body.add(List.of(nz(r.code()), nz(r.title()), nz(r.departmentName()),
                    nz(r.jobFamily()), nz(r.jobLevel()), nz(r.status())));
        }
        DocxBuilder.table(pkg.getMainDocumentPart(), HEADERS, body);
        DocxBuilder.write(pkg, out);
    }

    private void renderXlsx(OutputStream out, List<ReportDataPort.PositionRow> rows) {
        List<String> cols = List.of("code", "title", "department", "job_family", "job_level", "status");
        List<Map<String, String>> dataRows = new ArrayList<>();
        for (ReportDataPort.PositionRow r : rows) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("code", nz(r.code()));
            m.put("title", nz(r.title()));
            m.put("department", nz(r.departmentName()));
            m.put("job_family", nz(r.jobFamily()));
            m.put("job_level", nz(r.jobLevel()));
            m.put("status", nz(r.status()));
            dataRows.add(m);
        }
        byte[] bytes = excel.write("PositionCatalog", cols, dataRows);
        try {
            out.write(bytes);
        } catch (java.io.IOException e) {
            throw new ReportTemplateException("XLSX_WRITE_FAILED", e);
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
