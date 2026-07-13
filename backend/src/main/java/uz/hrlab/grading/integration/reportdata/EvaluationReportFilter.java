package uz.hrlab.grading.integration.reportdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import uz.hrlab.grading.common.exception.ValidationException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Structured filter for the EVALUATION_SUMMARY report, parsed from the opaque
 * {@code reports.filter_params} TEXT carrier (a JSON object) at render time.
 *
 * <p>WIRE CONTRACT (owner-confirmed): {@code filter_params} stays a single
 * {@code TEXT}/String column holding a JSON object with snake_case keys:
 * <pre>
 * {
 *   "methodology_version_ids": ["&lt;uuid&gt;", ...],   // D1: VERSION granularity, multi (IN list)
 *   "date_from": "YYYY-MM-DD",                            // calendar date, start-of-day inclusive
 *   "date_to":   "YYYY-MM-DD",                            // calendar date, end-of-day inclusive
 *   "evaluator_user_ids": ["&lt;uuid&gt;", ...]          // D3: per-evaluator-sheet, multi (IN list)
 * }
 * </pre>
 *
 * <p>Semantics (PRD §4): absent / null / empty array = that dimension is NOT
 * applied (no narrowing). Date inputs are CALENDAR dates: {@code date_from} is
 * expanded to start-of-day inclusive and {@code date_to} to end-of-day
 * inclusive, normalized to UTC {@link OffsetDateTime} — mirroring the existing
 * AUDIT_SUMMARY {@code from}/{@code to} {@code OffsetDateTime} shape on
 * {@link ReportDataPort#loadAuditEvents}. The date field filtered on is
 * {@code EvaluationJpaEntity.submittedAt} (decision D2).
 *
 * <p>Parsing is lenient about MISSING keys (any subset may be present) but
 * STRICT about malformed JSON / unparseable values — a broken {@code
 * filter_params} fails clearly ({@link ValidationException}) rather than
 * silently loading everything (which would be a security-relevant
 * over-disclosure).
 */
public record EvaluationReportFilter(
        List<UUID> methodologyVersionIds,
        OffsetDateTime dateFrom,
        OffsetDateTime dateTo,
        List<UUID> evaluatorUserIds) {

    public EvaluationReportFilter {
        methodologyVersionIds = methodologyVersionIds == null
                ? List.of() : List.copyOf(methodologyVersionIds);
        evaluatorUserIds = evaluatorUserIds == null
                ? List.of() : List.copyOf(evaluatorUserIds);
    }

    /** The no-op filter (no narrowing on any dimension) — preserves legacy behaviour. */
    public static EvaluationReportFilter none() {
        return new EvaluationReportFilter(List.of(), null, null, List.of());
    }

    /** True when no dimension narrows the result set (legacy / unfiltered path). */
    public boolean isEmpty() {
        return methodologyVersionIds.isEmpty()
                && dateFrom == null
                && dateTo == null
                && evaluatorUserIds.isEmpty();
    }

    /**
     * Parse the {@code filter_params} JSON STRING into a structured filter.
     * Null / blank input ⇒ {@link #none()}. Missing keys are tolerated. A
     * malformed JSON document or an unparseable id / date throws
     * {@link ValidationException} (code {@code REPORT_FILTER_MALFORMED}).
     *
     * <p>Bounds are CALENDAR dates: {@code date_from} → 00:00:00Z inclusive,
     * {@code date_to} → 23:59:59.999999999Z inclusive (whole-day inclusive).
     */
    public static EvaluationReportFilter parse(String filterParams, ObjectMapper mapper) {
        if (filterParams == null || filterParams.isBlank()) {
            return none();
        }
        JsonNode root;
        try {
            root = mapper.readTree(filterParams);
        } catch (Exception e) {
            throw new ValidationException("REPORT_FILTER_MALFORMED", "REPORT_FILTER_MALFORMED");
        }
        if (root == null || root.isNull()) {
            return none();
        }
        if (!root.isObject()) {
            throw new ValidationException("REPORT_FILTER_MALFORMED", "REPORT_FILTER_MALFORMED");
        }

        List<UUID> methodologyVersionIds = readUuidArray(root, "methodology_version_ids");
        List<UUID> evaluatorUserIds = readUuidArray(root, "evaluator_user_ids");
        OffsetDateTime dateFrom = readDate(root, "date_from", /*endOfDay=*/false);
        OffsetDateTime dateTo = readDate(root, "date_to", /*endOfDay=*/true);

        return new EvaluationReportFilter(methodologyVersionIds, dateFrom, dateTo, evaluatorUserIds);
    }

    private static List<UUID> readUuidArray(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new ValidationException("REPORT_FILTER_MALFORMED", "REPORT_FILTER_MALFORMED");
        }
        List<UUID> ids = new ArrayList<>(node.size());
        for (JsonNode el : node) {
            if (el == null || el.isNull()) continue;
            String raw = el.asText(null);
            if (raw == null || raw.isBlank()) continue;
            try {
                ids.add(UUID.fromString(raw.trim()));
            } catch (IllegalArgumentException ex) {
                throw new ValidationException("REPORT_FILTER_MALFORMED", "REPORT_FILTER_MALFORMED");
            }
        }
        return Collections.unmodifiableList(ids);
    }

    private static OffsetDateTime readDate(JsonNode root, String field, boolean endOfDay) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String raw = node.asText(null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        LocalDate day;
        try {
            day = LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new ValidationException("REPORT_FILTER_MALFORMED", "REPORT_FILTER_MALFORMED");
        }
        // Calendar date → whole-day inclusive range in UTC (no tenant timezone
        // configuration exists today; mirror loadAuditEvents OffsetDateTime shape).
        return endOfDay
                ? day.atTime(23, 59, 59, 999_999_999).atOffset(ZoneOffset.UTC)
                : day.atStartOfDay().atOffset(ZoneOffset.UTC);
    }
}
