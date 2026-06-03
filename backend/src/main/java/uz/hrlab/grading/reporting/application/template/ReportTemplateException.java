package uz.hrlab.grading.reporting.application.template;

import uz.hrlab.grading.common.exception.BaseDomainException;

/** Rendering failure inside a {@link ReportTemplate}. */
public class ReportTemplateException extends BaseDomainException {

    public ReportTemplateException(String safeMessage) {
        super("REPORT_TEMPLATE_FAILURE", safeMessage);
    }

    public ReportTemplateException(String safeMessage, Throwable cause) {
        super("REPORT_TEMPLATE_FAILURE", safeMessage, cause);
    }
}
