package uz.hrlab.grading.reporting.application.template;

import uz.hrlab.grading.common.exception.ReportTemplateException;
import uz.hrlab.grading.reporting.domain.ReportFormat;

import java.io.OutputStream;

/**
 * Shared boilerplate for the canonical {@link ReportTemplate} implementations:
 * the standard PDF/DOCX/XLSX {@link #supports(ReportFormat)} contract, the
 * format-dispatch {@code switch} (with the canonical {@code UNSUPPORTED_FORMAT}
 * failure), and the XLSX byte-flush + {@code nz} null-guard helpers.
 *
 * <p>Subclasses provide their own data payload type {@code T} and the three
 * per-format renderers, so this base changes NO output bytes — it only removes
 * the structural duplication that jscpd flagged across the {@code impl}
 * templates. Templates needing a different format set (e.g. a future PDF-only
 * report) simply override {@link #supports(ReportFormat)}.
 */
public abstract class AbstractReportTemplate<T> implements ReportTemplate {

    @Override
    public boolean supports(ReportFormat format) {
        return format == ReportFormat.PDF
                || format == ReportFormat.DOCX
                || format == ReportFormat.XLSX;
    }

    @Override
    public final void render(ReportGenerationContext ctx, OutputStream out) {
        T model = loadData(ctx);
        switch (ctx.format()) {
            case PDF -> renderPdf(ctx, out, model);
            case DOCX -> renderDocx(ctx, out, model);
            case XLSX -> renderXlsx(ctx, out, model);
            default -> throw new ReportTemplateException("UNSUPPORTED_FORMAT: " + ctx.format());
        }
    }

    /** Load the report-specific payload (data-port call) before format dispatch. */
    protected abstract T loadData(ReportGenerationContext ctx);

    protected abstract void renderPdf(ReportGenerationContext ctx, OutputStream out, T model);

    protected abstract void renderDocx(ReportGenerationContext ctx, OutputStream out, T model);

    protected abstract void renderXlsx(ReportGenerationContext ctx, OutputStream out, T model);

    /** Flush an XLSX byte buffer, mapping IO failures to the canonical template error. */
    protected static void writeXlsx(OutputStream out, byte[] bytes) {
        try {
            out.write(bytes);
        } catch (java.io.IOException e) {
            throw new ReportTemplateException("XLSX_WRITE_FAILED", e);
        }
    }

    /** Null-safe string for table/cell values. */
    protected static String nz(String s) {
        return s == null ? "" : s;
    }
}
