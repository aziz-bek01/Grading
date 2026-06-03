package uz.hrlab.grading.reporting.application.template;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.reporting.domain.ReportFormat;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Lookup the {@link ReportTemplate} for a {@link ReportType} + {@link ReportFormat}
 * combination. All beans implementing {@code ReportTemplate} are wired in by
 * Spring; one slot per type is enforced at startup.
 */
@Component
public class ReportTemplateRegistry {

    private final Map<ReportType, ReportTemplate> byType = new EnumMap<>(ReportType.class);

    public ReportTemplateRegistry(List<ReportTemplate> templates) {
        for (ReportTemplate t : templates) {
            ReportTemplate prev = byType.put(t.reportType(), t);
            if (prev != null) {
                throw new IllegalStateException(
                        "Duplicate ReportTemplate for type=" + t.reportType()
                                + ": " + prev.getClass().getSimpleName()
                                + " vs " + t.getClass().getSimpleName());
            }
        }
    }

    public ReportTemplate require(ReportType type, ReportFormat format) {
        ReportTemplate t = byType.get(type);
        if (t == null) {
            throw new ReportTemplateException("No ReportTemplate registered for type=" + type);
        }
        if (!t.supports(format)) {
            throw new ReportTemplateException(
                    "ReportTemplate " + t.getClass().getSimpleName()
                            + " does not support format=" + format);
        }
        return t;
    }
}
