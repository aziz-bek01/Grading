package uz.hrlab.grading.reporting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.ScoringMode;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.reporting.application.template.EvaluationReportFilter;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Request-time validation: exactly one methodology version is mandatory for
 * the evaluation-bearing report types (each version defines its own factor
 * set); an invalid date range and a cross-tenant methodology version are
 * rejected; evaluator ids are fail-soft (not validated); non-evaluation report
 * types skip structured validation.
 */
class EvaluationReportFilterValidatorTest {

    private final MethodologyVersionRepository methodologyVersions =
            mock(MethodologyVersionRepository.class);
    private final EvaluationReportFilterValidator validator =
            new EvaluationReportFilterValidator(methodologyVersions, new ObjectMapper());

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void methodologyRequiredWhenAbsent() {
        // Empty filter (no methodology) is no longer "all versions" — a report
        // must be scoped to exactly one methodology version.
        assertThatThrownBy(() ->
                validator.validate(ReportType.EVALUATION_SUMMARY, tenantId, "{}"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("REPORT_FILTER_METHODOLOGY_REQUIRED");
    }

    @Test
    void multipleMethodologyVersionsRejected() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        String json = "{\"methodology_version_ids\":[\"%s\",\"%s\"]}".formatted(a, b);
        assertThatThrownBy(() ->
                validator.validate(ReportType.EVALUATION_SUMMARY, tenantId, json))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("REPORT_FILTER_METHODOLOGY_REQUIRED");
    }

    @Test
    void dateFromAfterDateToRejected() {
        UUID m = UUID.randomUUID();
        when(methodologyVersions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(version(m)));
        String json = ("{\"methodology_version_ids\":[\"%s\"],"
                + "\"date_from\":\"2026-06-30\",\"date_to\":\"2026-04-01\"}").formatted(m);
        assertThatThrownBy(() ->
                validator.validate(ReportType.EVALUATION_SUMMARY, tenantId, json))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("REPORT_FILTER_INVALID_DATE_RANGE");
    }

    @Test
    void equalDatesAllowed() {
        UUID m = UUID.randomUUID();
        when(methodologyVersions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(version(m)));
        String json = ("{\"methodology_version_ids\":[\"%s\"],"
                + "\"date_from\":\"2026-04-01\",\"date_to\":\"2026-04-01\"}").formatted(m);
        assertThatCode(() ->
                validator.validate(ReportType.EVALUATION_SUMMARY, tenantId, json))
                .doesNotThrowAnyException();
    }

    @Test
    void methodologyVersionNotOwnedByTenantRejected() {
        UUID foreign = UUID.randomUUID();
        // Repo returns nothing for the (foreign) id → ownership check fails.
        when(methodologyVersions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of());

        String json = "{\"methodology_version_ids\":[\"%s\"]}".formatted(foreign);
        assertThatThrownBy(() ->
                validator.validate(ReportType.EVALUATION_SUMMARY, tenantId, json))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("REPORT_FILTER_INVALID_METHODOLOGY");
    }

    @Test
    void singleOwnedMethodologyVersionAccepted() {
        UUID a = UUID.randomUUID();
        when(methodologyVersions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(version(a)));

        String json = "{\"methodology_version_ids\":[\"%s\"]}".formatted(a);
        EvaluationReportFilter f =
                validator.validate(ReportType.EVALUATION_SUMMARY, tenantId, json);
        assertThat(f.methodologyVersionIds()).containsExactly(a);
    }

    @Test
    void evaluatorIdsAreFailSoft_neverRejected() {
        // A random / foreign evaluator id must NOT cause a request rejection
        // (PRD AC-3.4: it simply contributes zero rows downstream).
        UUID m = UUID.randomUUID();
        when(methodologyVersions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(version(m)));
        String json = ("{\"methodology_version_ids\":[\"%s\"],"
                + "\"evaluator_user_ids\":[\"%s\"]}").formatted(m, UUID.randomUUID());
        assertThatCode(() ->
                validator.validate(ReportType.EVALUATION_SUMMARY, tenantId, json))
                .doesNotThrowAnyException();
    }

    @Test
    void executiveSummaryRequiresMethodology() {
        // The mandatory single-methodology rule applies to EXECUTIVE_SUMMARY too
        // (it consumes the same evaluation filter).
        assertThatThrownBy(() ->
                validator.validate(ReportType.EXECUTIVE_SUMMARY, tenantId, "{}"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("REPORT_FILTER_METHODOLOGY_REQUIRED");
    }

    @Test
    void executiveSummaryAlsoValidatesDateRange() {
        // The same date-range gate applies to EXECUTIVE_SUMMARY (with the now
        // mandatory single methodology present).
        UUID m = UUID.randomUUID();
        when(methodologyVersions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(version(m)));
        String json = ("{\"methodology_version_ids\":[\"%s\"],"
                + "\"date_from\":\"2026-06-30\",\"date_to\":\"2026-04-01\"}").formatted(m);
        assertThatThrownBy(() ->
                validator.validate(ReportType.EXECUTIVE_SUMMARY, tenantId, json))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("REPORT_FILTER_INVALID_DATE_RANGE");
    }

    @Test
    void executiveSummaryValidatesMethodologyOwnership() {
        UUID foreign = UUID.randomUUID();
        when(methodologyVersions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of());

        String json = "{\"methodology_version_ids\":[\"%s\"]}".formatted(foreign);
        assertThatThrownBy(() ->
                validator.validate(ReportType.EXECUTIVE_SUMMARY, tenantId, json))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("REPORT_FILTER_INVALID_METHODOLOGY");
    }

    @Test
    void nonEvaluationSummaryTypeSkipsStructuredValidation() {
        // Even an "invalid" date range / missing methodology is not validated for
        // other report types — the filter stays opaque for them.
        String json = "{\"date_from\":\"2026-06-30\",\"date_to\":\"2026-04-01\"}";
        assertThatCode(() ->
                validator.validate(ReportType.AUDIT_SUMMARY, tenantId, json))
                .doesNotThrowAnyException();
    }

    @Test
    void malformedJsonRejectedRegardlessOfType() {
        assertThatThrownBy(() ->
                validator.validate(ReportType.EVALUATION_SUMMARY, tenantId, "{broken"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("REPORT_FILTER_MALFORMED");
    }

    private static MethodologyVersionJpaEntity version(UUID id) {
        return new MethodologyVersionJpaEntity(id, UUID.randomUUID(), UUID.randomUUID(),
                1, MethodologyVersionStatus.APPROVED, ScoringMode.DIRECT_POINTS, null, null);
    }
}
