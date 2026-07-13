package uz.hrlab.grading.common.exception;

/** Rendering failure inside a report template. */
public class ReportTemplateException extends BaseDomainException {

    public ReportTemplateException(String safeMessage) {
        super("REPORT_TEMPLATE_FAILURE", safeMessage);
    }

    public ReportTemplateException(String safeMessage, Throwable cause) {
        super("REPORT_TEMPLATE_FAILURE", safeMessage, cause);
    }
}
