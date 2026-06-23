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

import java.util.Collection;
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
 * Request-time validation: invalid date range and cross-tenant methodology
 * version are rejected; evaluator ids are fail-soft (not validated); non-
 * EVALUATION_SUMMARY report types skip structured validation.
 */
class EvaluationReportFilterValidatorTest {

    private final MethodologyVersionRepository methodologyVersions =
            mock(MethodologyVersionRepository.class);
    private final EvaluationReportFilterValidator validator =
            new EvaluationReportFilterValidator(methodologyVersions, new ObjectMapper());

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void dateFromAfterDateToRejected() {
        String json = "{\"date_from\":\"2026-06-30\",\"date_to\":\"2026-04-01\"}";
        assertThatThrownBy(() ->
                validator.validate(ReportType.EVALUATION_SUMMARY, tenantId, json))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("REPORT_FILTER_INVALID_DATE_RANGE");
    }

    @Test
    void equalDatesAllowed() {
        String json = "{\"date_from\":\"2026-04-01\",\"date_to\":\"2026-04-01\"}";
        assertThatCode(() ->
                validator.validate(ReportType.EVALUATION_SUMMARY, tenantId, json))
                .doesNotThrowAnyException();
    }

    @Test
    void methodologyVersionNotOwnedByTenantRejected() {
        UUID owned = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        // Repo returns only the tenant-owned version → the foreign id is missing.
        when(methodologyVersions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(version(owned)));

        String json = "{\"methodology_version_ids\":[\"%s\",\"%s\"]}".formatted(owned, foreign);
        assertThatThrownBy(() ->
                validator.validate(ReportType.EVALUATION_SUMMARY, tenantId, json))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("REPORT_FILTER_INVALID_METHODOLOGY");
    }

    @Test
    void allMethodologyVersionsOwnedAccepted() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(methodologyVersions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(version(a), version(b)));

        String json = "{\"methodology_version_ids\":[\"%s\",\"%s\"]}".formatted(a, b);
        EvaluationReportFilter f =
                validator.validate(ReportType.EVALUATION_SUMMARY, tenantId, json);
        assertThat(f.methodologyVersionIds()).containsExactlyInAnyOrder(a, b);
    }

    @Test
    void evaluatorIdsAreFailSoft_neverRejected() {
        // A random / foreign evaluator id must NOT cause a request rejection
        // (PRD AC-3.4: it simply contributes zero rows downstream).
        String json = "{\"evaluator_user_ids\":[\"%s\"]}".formatted(UUID.randomUUID());
        assertThatCode(() ->
                validator.validate(ReportType.EVALUATION_SUMMARY, tenantId, json))
                .doesNotThrowAnyException();
    }

    @Test
    void executiveSummaryAlsoValidatesDateRange() {
        // The same date-range gate now applies to EXECUTIVE_SUMMARY (it consumes
        // the same evaluation filter for its evaluation-scoped KPIs).
        String json = "{\"date_from\":\"2026-06-30\",\"date_to\":\"2026-04-01\"}";
        assertThatThrownBy(() ->
                validator.validate(ReportType.EXECUTIVE_SUMMARY, tenantId, json))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("REPORT_FILTER_INVALID_DATE_RANGE");
    }

    @Test
    void executiveSummaryValidatesMethodologyOwnership() {
        UUID owned = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        when(methodologyVersions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(version(owned)));

        String json = "{\"methodology_version_ids\":[\"%s\",\"%s\"]}".formatted(owned, foreign);
        assertThatThrownBy(() ->
                validator.validate(ReportType.EXECUTIVE_SUMMARY, tenantId, json))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("REPORT_FILTER_INVALID_METHODOLOGY");
    }

    @Test
    void nonEvaluationSummaryTypeSkipsStructuredValidation() {
        // Even an "invalid" date range is not validated for other report types —
        // the filter stays opaque for them.
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
