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
 * Full methodology specification — metadata + factors + level definitions
 * (multilingual title). PDF + DOCX + XLSX implemented.
 */
@Component
public class MethodologySpecReport implements ReportTemplate {

    private final ReportDataPort data;
    private final ExcelWriter excel;

    public MethodologySpecReport(ReportDataPort data, ExcelWriter excel) {
        this.data = data;
        this.excel = excel;
    }

    @Override public ReportType reportType() { return ReportType.METHODOLOGY_SPEC; }

    @Override public boolean supports(ReportFormat format) {
        return format == ReportFormat.PDF
                || format == ReportFormat.DOCX
                || format == ReportFormat.XLSX;
    }

    @Override
    public void render(ReportGenerationContext ctx, OutputStream out) {
        ReportDataPort.MethodologySpec spec =
                data.methodologySpec(ctx.tenantId(), ctx.projectId(), ctx.locale());
        switch (ctx.format()) {
            case PDF -> renderPdf(ctx, out, spec);
            case DOCX -> renderDocx(ctx, out, spec);
            case XLSX -> renderXlsx(out, spec);
            default -> throw new ReportTemplateException("UNSUPPORTED_FORMAT: " + ctx.format());
        }
    }

    private void renderPdf(ReportGenerationContext ctx, OutputStream out,
                           ReportDataPort.MethodologySpec spec) {
        Document doc = PdfBuilder.open(out);
        try {
            PdfBuilder.heading(doc, ctx.title());
            PdfBuilder.metaLine(doc, "Methodology", spec.methodologyName());
            PdfBuilder.metaLine(doc, "Version", spec.versionLabel());
            PdfBuilder.metaLine(doc, "Status", spec.status());
            for (ReportDataPort.FactorRow f : spec.factors()) {
                PdfBuilder.subheading(doc, f.code() + " — " + f.title());
                PdfBuilder.metaLine(doc, "Weight", String.valueOf(f.weight()));
                PdfBuilder.metaLine(doc, "Max points", String.valueOf(f.maxPoints()));
                PdfBuilder.metaLine(doc, "Scoring mode", f.scoringMode());
                List<List<String>> tbl = new ArrayList<>();
                for (Map<String, String> level : f.levels()) {
                    tbl.add(List.of(
                            level.getOrDefault("code", ""),
                            level.getOrDefault("title", ""),
                            level.getOrDefault("points", ""),
                            level.getOrDefault("scaleValue", "")));
                }
                PdfBuilder.table(doc, List.of("Level", "Title", "Points", "Scale"), tbl);
            }
            PdfBuilder.footer(doc, OffsetDateTime.now(), null);
        } finally {
            PdfBuilder.close(doc);
        }
    }

    private void renderDocx(ReportGenerationContext ctx, OutputStream out,
                            ReportDataPort.MethodologySpec spec) {
        WordprocessingMLPackage pkg = DocxBuilder.create();
        DocxBuilder.heading(pkg.getMainDocumentPart(), ctx.title());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Methodology", spec.methodologyName());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Version", spec.versionLabel());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Status", spec.status());
        for (ReportDataPort.FactorRow f : spec.factors()) {
            DocxBuilder.subheading(pkg.getMainDocumentPart(),
                    f.code() + " — " + f.title()
                            + " (w=" + f.weight() + ", max=" + f.maxPoints() + ")");
            List<List<String>> body = new ArrayList<>();
            for (Map<String, String> level : f.levels()) {
                body.add(List.of(
                        level.getOrDefault("code", ""),
                        level.getOrDefault("title", ""),
                        level.getOrDefault("points", ""),
                        level.getOrDefault("scaleValue", "")));
            }
            DocxBuilder.table(pkg.getMainDocumentPart(),
                    List.of("Level", "Title", "Points", "Scale"), body);
        }
        DocxBuilder.write(pkg, out);
    }

    private void renderXlsx(OutputStream out, ReportDataPort.MethodologySpec spec) {
        List<String> cols = List.of("factor_code", "factor_title", "weight", "max_points",
                "scoring_mode", "level_code", "level_title", "points", "scale_value");
        List<Map<String, String>> rows = new ArrayList<>();
        for (ReportDataPort.FactorRow f : spec.factors()) {
            for (Map<String, String> level : f.levels()) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("factor_code", f.code());
                m.put("factor_title", f.title());
                m.put("weight", String.valueOf(f.weight()));
                m.put("max_points", String.valueOf(f.maxPoints()));
                m.put("scoring_mode", f.scoringMode());
                m.put("level_code", level.getOrDefault("code", ""));
                m.put("level_title", level.getOrDefault("title", ""));
                m.put("points", level.getOrDefault("points", ""));
                m.put("scale_value", level.getOrDefault("scaleValue", ""));
                rows.add(m);
            }
        }
        byte[] bytes = excel.write("MethodologySpec", cols, rows);
        try {
            out.write(bytes);
        } catch (java.io.IOException e) {
            throw new ReportTemplateException("XLSX_WRITE_FAILED", e);
        }
    }
}
