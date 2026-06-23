package uz.hrlab.grading.reporting.application.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.common.exception.ValidationException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for the {@code filter_params} JSON parse contract
 * (snake_case keys, lenient missing keys, strict malformed JSON, calendar-date
 * inclusive bounds). No DB / Spring context required.
 */
class EvaluationReportFilterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void nullOrBlankParsesToNoneFilter() {
        assertThat(EvaluationReportFilter.parse(null, mapper).isEmpty()).isTrue();
        assertThat(EvaluationReportFilter.parse("", mapper).isEmpty()).isTrue();
        assertThat(EvaluationReportFilter.parse("   ", mapper).isEmpty()).isTrue();
        assertThat(EvaluationReportFilter.parse("{}", mapper).isEmpty()).isTrue();
    }

    @Test
    void parsesAllFourSnakeCaseKeys() {
        UUID mv1 = UUID.randomUUID();
        UUID mv2 = UUID.randomUUID();
        UUID ev1 = UUID.randomUUID();
        String json = """
                {
                  "methodology_version_ids": ["%s", "%s"],
                  "date_from": "2026-04-01",
                  "date_to": "2026-06-30",
                  "evaluator_user_ids": ["%s"]
                }
                """.formatted(mv1, mv2, ev1);

        EvaluationReportFilter f = EvaluationReportFilter.parse(json, mapper);

        assertThat(f.isEmpty()).isFalse();
        assertThat(f.methodologyVersionIds()).containsExactly(mv1, mv2);
        assertThat(f.evaluatorUserIds()).containsExactly(ev1);
        // date_from → start-of-day inclusive (UTC); date_to → end-of-day inclusive.
        assertThat(f.dateFrom())
                .isEqualTo(OffsetDateTime.of(2026, 4, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(f.dateTo())
                .isEqualTo(OffsetDateTime.of(2026, 6, 30, 23, 59, 59, 999_999_999, ZoneOffset.UTC));
    }

    @Test
    void missingKeysAreTolerated_onlyDateFrom() {
        EvaluationReportFilter f =
                EvaluationReportFilter.parse("{\"date_from\":\"2026-01-15\"}", mapper);
        assertThat(f.methodologyVersionIds()).isEmpty();
        assertThat(f.evaluatorUserIds()).isEmpty();
        assertThat(f.dateFrom())
                .isEqualTo(OffsetDateTime.of(2026, 1, 15, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(f.dateTo()).isNull();
        assertThat(f.isEmpty()).isFalse();
    }

    @Test
    void emptyArraysAreTreatedAsNoNarrowing() {
        EvaluationReportFilter f = EvaluationReportFilter.parse(
                "{\"methodology_version_ids\":[],\"evaluator_user_ids\":[]}", mapper);
        assertThat(f.methodologyVersionIds()).isEmpty();
        assertThat(f.evaluatorUserIds()).isEmpty();
        assertThat(f.isEmpty()).isTrue();
    }

    @Test
    void nullArrayElementsAndBlankStringsAreSkipped() {
        UUID mv = UUID.randomUUID();
        String json = "{\"methodology_version_ids\":[null, \"\", \"  \", \"%s\"]}".formatted(mv);
        EvaluationReportFilter f = EvaluationReportFilter.parse(json, mapper);
        assertThat(f.methodologyVersionIds()).containsExactly(mv);
    }

    @Test
    void malformedJsonFailsLoudly_notSilentlyUnfiltered() {
        assertThatThrownBy(() -> EvaluationReportFilter.parse("{not json", mapper))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("REPORT_FILTER_MALFORMED");
    }

    @Test
    void nonObjectJsonRejected() {
        assertThatThrownBy(() -> EvaluationReportFilter.parse("[1,2,3]", mapper))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("REPORT_FILTER_MALFORMED");
    }

    @Test
    void invalidUuidInArrayRejected() {
        assertThatThrownBy(() -> EvaluationReportFilter.parse(
                "{\"evaluator_user_ids\":[\"not-a-uuid\"]}", mapper))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("REPORT_FILTER_MALFORMED");
    }

    @Test
    void invalidDateRejected() {
        assertThatThrownBy(() -> EvaluationReportFilter.parse(
                "{\"date_from\":\"2026-13-99\"}", mapper))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("REPORT_FILTER_MALFORMED");
    }

    @Test
    void recordCanonicalizesNullCollectionsToEmpty() {
        EvaluationReportFilter f = new EvaluationReportFilter(null, null, null, null);
        assertThat(f.methodologyVersionIds()).isEmpty();
        assertThat(f.evaluatorUserIds()).isEmpty();
        assertThat(f.isEmpty()).isTrue();
    }
}
