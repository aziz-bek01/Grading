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
 * Full methodology specification — metadata + factors + level definitions
 * (localized title/label). PDF + DOCX + XLSX implemented.
 *
 * <p>The level table reads the {@code "label"} key the port actually writes
 * (was {@code "title"}, which left the column blank).
 */
@Component
public class MethodologySpecReport
        extends AbstractReportTemplate<ReportDataPort.MethodologySpec> {

    private final ReportDataPort data;
    private final ExcelWriter excel;

    public MethodologySpecReport(ReportDataPort data, ExcelWriter excel) {
        this.data = data;
        this.excel = excel;
    }

    @Override public ReportType reportType() { return ReportType.METHODOLOGY_SPEC; }

    @Override
    protected ReportDataPort.MethodologySpec loadData(ReportGenerationContext ctx) {
        return data.methodologySpec(ctx.tenantId(), ctx.projectId(), ctx.locale());
    }

    private static List<String> levelHeaders(String locale) {
        return List.of(
                ReportLabels.label("col.level", locale),
                ReportLabels.label("col.title", locale),
                ReportLabels.label("col.points", locale),
                ReportLabels.label("col.scale", locale));
    }

    @Override
    protected void renderPdf(ReportGenerationContext ctx, OutputStream out,
                             ReportDataPort.MethodologySpec spec) {
        String locale = ctx.locale();
        Document doc = PdfBuilder.open(out);
        try {
            PdfBuilder.heading(doc, ctx.title());
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.methodology", locale), spec.methodologyName());
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.version", locale), spec.versionLabel());
            PdfBuilder.metaLine(doc, ReportLabels.label("meta.status", locale), spec.status());
            if (spec.factors().isEmpty()) {
                PdfBuilder.paragraph(doc, ReportLabels.label("empty.factors", locale));
            }
            for (ReportDataPort.FactorRow f : spec.factors()) {
                PdfBuilder.subheading(doc, f.code() + " — " + f.title());
                PdfBuilder.metaLine(doc, ReportLabels.label("meta.weight", locale), String.valueOf(f.weight()));
                PdfBuilder.metaLine(doc, ReportLabels.label("meta.maxPoints", locale),
                        String.valueOf(f.maxPoints()));
                PdfBuilder.metaLine(doc, ReportLabels.label("meta.scoringMode", locale), f.scoringMode());
                List<List<String>> tbl = new ArrayList<>();
                for (Map<String, String> level : f.levels()) {
                    tbl.add(List.of(
                            level.getOrDefault("code", ""),
                            level.getOrDefault("label", ""),
                            level.getOrDefault("points", ""),
                            level.getOrDefault("scaleValue", "")));
                }
                PdfBuilder.table(doc, levelHeaders(locale), tbl);
            }
            PdfBuilder.footer(doc, OffsetDateTime.now(), null);
        } finally {
            PdfBuilder.close(doc);
        }
    }

    @Override
    protected void renderDocx(ReportGenerationContext ctx, OutputStream out,
                              ReportDataPort.MethodologySpec spec) {
        String locale = ctx.locale();
        WordprocessingMLPackage pkg = DocxBuilder.create();
        DocxBuilder.heading(pkg.getMainDocumentPart(), ctx.title());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.methodology", locale), spec.methodologyName());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.version", locale), spec.versionLabel());
        DocxBuilder.metaLine(pkg.getMainDocumentPart(),
                ReportLabels.label("meta.status", locale), spec.status());
        if (spec.factors().isEmpty()) {
            DocxBuilder.paragraph(pkg.getMainDocumentPart(),
                    ReportLabels.label("empty.factors", locale));
        }
        for (ReportDataPort.FactorRow f : spec.factors()) {
            DocxBuilder.subheading(pkg.getMainDocumentPart(),
                    f.code() + " — " + f.title()
                            + " (w=" + f.weight() + ", max=" + f.maxPoints() + ")");
            List<List<String>> body = new ArrayList<>();
            for (Map<String, String> level : f.levels()) {
                body.add(List.of(
                        level.getOrDefault("code", ""),
                        level.getOrDefault("label", ""),
                        level.getOrDefault("points", ""),
                        level.getOrDefault("scaleValue", "")));
            }
            DocxBuilder.table(pkg.getMainDocumentPart(), levelHeaders(locale), body);
        }
        DocxBuilder.write(pkg, out);
    }

    @Override
    protected void renderXlsx(ReportGenerationContext ctx, OutputStream out,
                              ReportDataPort.MethodologySpec spec) {
        String locale = ctx.locale();
        List<String> displayHeaders = List.of(
                ReportLabels.label("col.code", locale),
                ReportLabels.label("col.title", locale),
                ReportLabels.label("meta.weight", locale),
                ReportLabels.label("meta.maxPoints", locale),
                ReportLabels.label("meta.scoringMode", locale),
                ReportLabels.label("col.level", locale),
                ReportLabels.label("col.title", locale),
                ReportLabels.label("col.points", locale),
                ReportLabels.label("col.scale", locale));
        List<String> dataKeys = List.of("factor_code", "factor_title", "weight", "max_points",
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
                m.put("level_title", level.getOrDefault("label", ""));
                m.put("points", level.getOrDefault("points", ""));
                m.put("scale_value", level.getOrDefault("scaleValue", ""));
                rows.add(m);
            }
        }
        writeXlsx(out, excel.write("MethodologySpec", displayHeaders, dataKeys, rows));
    }
}
