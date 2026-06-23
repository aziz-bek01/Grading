package uz.hrlab.grading.reporting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.reporting.application.template.EvaluationReportFilter;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Request-time validation for the structured evaluation filter shared by the
 * EVALUATION_SUMMARY and EXECUTIVE_SUMMARY report types (PRD §4.2 AC-2.4,
 * §6-D-validation). Fails fast at request time so a clearly invalid filter
 * never reaches the async worker:
 *
 * <ul>
 *   <li>{@code date_from > date_to} ⇒ {@code REPORT_FILTER_INVALID_DATE_RANGE}
 *       (do NOT silently swap);</li>
 *   <li>any {@code methodology_version_id} not owned by the active tenant ⇒
 *       {@code REPORT_FILTER_INVALID_METHODOLOGY} (tenant ownership is the
 *       enforceable scope at request time — methodology versions carry no
 *       project_id; a tenant-owned-but-wrong-project version is naturally a
 *       zero-row result in the filtered query, NFR-2 / EC-2).</li>
 * </ul>
 *
 * <p>Evaluator ids are deliberately NOT existence-validated here: a stale /
 * foreign evaluator id simply contributes zero rows (PRD AC-3.4 fail-soft), so
 * rejecting the whole request would leak existence information. Malformed JSON
 * is surfaced by {@link EvaluationReportFilter#parse} as
 * {@code REPORT_FILTER_MALFORMED}.
 */
@Component
public class EvaluationReportFilterValidator {

    private final MethodologyVersionRepository methodologyVersions;
    private final ObjectMapper objectMapper;

    public EvaluationReportFilterValidator(MethodologyVersionRepository methodologyVersions,
                                           ObjectMapper objectMapper) {
        this.methodologyVersions = methodologyVersions;
        this.objectMapper = objectMapper;
    }

    /**
     * Parse + validate the {@code filterParams} for the given report type and
     * tenant. Returns the parsed filter (for reuse, e.g. audit cardinality).
     * Filters are only meaningful for the evaluation-bearing report types
     * (EVALUATION_SUMMARY, EXECUTIVE_SUMMARY); for other report types the raw
     * string is left opaque and no structured validation runs.
     */
    public EvaluationReportFilter validate(ReportType type, UUID tenantId, String filterParams) {
        EvaluationReportFilter filter = EvaluationReportFilter.parse(filterParams, objectMapper);
        // Structured validation runs for the two evaluation-bearing report types
        // that consume the filter: EVALUATION_SUMMARY and EXECUTIVE_SUMMARY. Other
        // report types leave the raw string opaque (no structured validation).
        if ((type != ReportType.EVALUATION_SUMMARY && type != ReportType.EXECUTIVE_SUMMARY)
                || filter.isEmpty()) {
            return filter;
        }

        if (filter.dateFrom() != null && filter.dateTo() != null
                && filter.dateFrom().isAfter(filter.dateTo())) {
            throw new ValidationException("REPORT_FILTER_INVALID_DATE_RANGE",
                    "REPORT_FILTER_INVALID_DATE_RANGE");
        }

        List<UUID> requested = filter.methodologyVersionIds();
        if (!requested.isEmpty()) {
            Set<UUID> distinct = new HashSet<>(requested);
            Set<UUID> owned = new HashSet<>();
            for (MethodologyVersionJpaEntity v :
                    methodologyVersions.findAllByTenantIdAndIdIn(tenantId, distinct)) {
                owned.add(v.getId());
            }
            if (!owned.containsAll(distinct)) {
                throw new ValidationException("REPORT_FILTER_INVALID_METHODOLOGY",
                        "REPORT_FILTER_INVALID_METHODOLOGY");
            }
        }

        return filter;
    }
}
