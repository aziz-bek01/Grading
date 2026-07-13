package uz.hrlab.grading.reporting.application.template;

import uz.hrlab.grading.common.exception.ReportTemplateException;
import uz.hrlab.grading.reporting.domain.ReportFormat;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.io.OutputStream;

/**
 * Strategy interface — one implementation per {@link ReportType}. Templates
 * are picked up by the worker via {@link ReportTemplateRegistry} from Spring's
 * application context, keyed by their {@link #reportType()} value.
 *
 * <p>Implementations MUST be side-effect-free; the worker takes care of
 * storage I/O and status transitions.
 */
public interface ReportTemplate {

    ReportType reportType();

    /** Whether this template supports the requested format. */
    boolean supports(ReportFormat format);

    /**
     * Render the report into the given output stream.
     *
     * @throws ReportTemplateException on render failure; the worker maps this
     *         to {@code status=FAILED} and writes {@code REPORT_FAILED} audit.
     */
    void render(ReportGenerationContext ctx, OutputStream out);
}
