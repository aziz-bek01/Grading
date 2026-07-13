package uz.hrlab.grading.reporting.application.template.impl;

import com.lowagie.text.Document;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import uz.hrlab.grading.integration.excel.ExcelWriter;
import uz.hrlab.grading.integration.docs.DocxBuilder;
import uz.hrlab.grading.integration.docs.PdfBuilder;
import uz.hrlab.grading.reporting.application.template.ReportGenerationContext;
import uz.hrlab.grading.common.exception.ReportTemplateException;

import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Renders a one-page "Coming in MVP 3" placeholder for the three stub report
 * types. Explicit per spec — we never silently no-op.
 */
final class PlaceholderRenderer {

    private PlaceholderRenderer() { }

    static final String PLACEHOLDER_MESSAGE =
            "This report template is reserved for MVP 3 — the surface is "
                    + "available so role grants and request flow can be exercised, "
                    + "but the data layer ships in the next release.";

    static void render(ReportGenerationContext ctx, OutputStream out, ExcelWriter excel) {
        switch (ctx.format()) {
            case PDF -> {
                Document doc = PdfBuilder.open(out);
                try {
                    PdfBuilder.heading(doc, ctx.title());
                    PdfBuilder.metaLine(doc, "Project", ctx.projectId().toString());
                    PdfBuilder.metaLine(doc, "Status", "Coming in MVP 3");
                    PdfBuilder.paragraph(doc, PLACEHOLDER_MESSAGE);
                    PdfBuilder.footer(doc, OffsetDateTime.now(), null);
                } finally {
                    PdfBuilder.close(doc);
                }
            }
            case DOCX -> {
                WordprocessingMLPackage pkg = DocxBuilder.create();
                DocxBuilder.heading(pkg.getMainDocumentPart(), ctx.title());
                DocxBuilder.metaLine(pkg.getMainDocumentPart(), "Status", "Coming in MVP 3");
                DocxBuilder.paragraph(pkg.getMainDocumentPart(), PLACEHOLDER_MESSAGE);
                DocxBuilder.write(pkg, out);
            }
            case XLSX -> {
                byte[] bytes = excel.write("Placeholder",
                        List.of("status", "message"),
                        List.of(Map.of("status", "Coming in MVP 3",
                                "message", PLACEHOLDER_MESSAGE)));
                try {
                    out.write(bytes);
                } catch (java.io.IOException e) {
                    throw new ReportTemplateException("XLSX_WRITE_FAILED", e);
                }
            }
            default -> throw new ReportTemplateException("UNSUPPORTED_FORMAT: " + ctx.format());
        }
    }
}
