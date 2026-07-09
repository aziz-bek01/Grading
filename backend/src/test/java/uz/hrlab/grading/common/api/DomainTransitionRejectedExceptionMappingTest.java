package uz.hrlab.grading.common.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ResponseEntity;
import uz.hrlab.grading.approval.domain.ApprovalTransitionRejectedException;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.DomainTransitionRejectedException;
import uz.hrlab.grading.evaluation.domain.EvaluationTransitionRejectedException;
import uz.hrlab.grading.evaluation.domain.PanelStatusTransitionRejectedException;
import uz.hrlab.grading.gradestructure.domain.GradeStructureTransitionRejectedException;
import uz.hrlab.grading.integration.exports.domain.ExportJobStatus;
import uz.hrlab.grading.integration.exports.domain.ExportJobTransitionRejectedException;
import uz.hrlab.grading.integration.imports.domain.ImportBatchStatus;
import uz.hrlab.grading.integration.imports.domain.ImportBatchTransitionRejectedException;
import uz.hrlab.grading.jobanalysis.domain.QuestionnaireTransitionRejectedException;
import uz.hrlab.grading.jobprofile.domain.JobProfileTransitionRejectedException;
import uz.hrlab.grading.methodology.domain.MethodologyVersionTransitionRejectedException;
import uz.hrlab.grading.reporting.domain.ReportStatus;
import uz.hrlab.grading.reporting.domain.ReportTransitionRejectedException;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * BE-014 contract test. Proves that EVERY domain transition-rejection exception
 * resolves to HTTP 409 CONFLICT through the single
 * {@link GlobalExceptionHandler#handleDomainTransition} handler, and that each
 * exception's stable error {@code code} is carried through unchanged.
 *
 * <p>Before BE-014 only four of these ten types were mapped to 409; the other
 * six fell through to the generic {@code BaseDomainException} handler and
 * returned 400, so clients could not distinguish "conflict / retry" from "bad
 * request". This test guards against that regression for all ten.
 *
 * <p>Follows the {@link CrossTenantAuditRecordingTest} style: the handler is
 * instantiated directly with a mocked {@link AuditService}, no Spring context.
 */
class DomainTransitionRejectedExceptionMappingTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(mock(AuditService.class));

    static Stream<Arguments> transitionRejections() {
        return Stream.of(
                Arguments.of(new QuestionnaireTransitionRejectedException("nope"),
                        "QUESTIONNAIRE_TRANSITION_REJECTED"),
                Arguments.of(new ApprovalTransitionRejectedException("nope"),
                        "APPROVAL_TRANSITION_REJECTED"),
                Arguments.of(new GradeStructureTransitionRejectedException("nope"),
                        "GRADE_STRUCTURE_TRANSITION_REJECTED"),
                Arguments.of(new EvaluationTransitionRejectedException("nope"),
                        "EVALUATION_TRANSITION_REJECTED"),
                Arguments.of(new PanelStatusTransitionRejectedException("nope"),
                        "PANEL_STATUS_TRANSITION_REJECTED"),
                Arguments.of(new MethodologyVersionTransitionRejectedException("nope"),
                        "METHODOLOGY_VERSION_TRANSITION_REJECTED"),
                Arguments.of(new JobProfileTransitionRejectedException("nope"),
                        "JOB_PROFILE_TRANSITION_REJECTED"),
                Arguments.of(new ImportBatchTransitionRejectedException(
                                ImportBatchStatus.UPLOADED, ImportBatchStatus.PARSING),
                        "IMPORT_BATCH_TRANSITION_REJECTED"),
                Arguments.of(new ExportJobTransitionRejectedException(
                                ExportJobStatus.REQUESTED, ExportJobStatus.GENERATED),
                        "EXPORT_JOB_TRANSITION_REJECTED"),
                Arguments.of(new ReportTransitionRejectedException(
                                ReportStatus.REQUESTED, ReportStatus.GENERATED),
                        "REPORT_TRANSITION_REJECTED"));
    }

    @ParameterizedTest(name = "{1} -> 409")
    @MethodSource("transitionRejections")
    void everyTransitionRejectionResolvesTo409(DomainTransitionRejectedException ex,
                                               String expectedCode) {
        ResponseEntity<ErrorResponse> response = handler.handleDomainTransition(ex);

        assertThat(response.getStatusCode().value())
                .as("%s must map to 409 CONFLICT", ex.getClass().getSimpleName())
                .isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code())
                .as("stable error code must be carried through unchanged")
                .isEqualTo(expectedCode);
    }

    @Test
    void allTenTransitionRejectionTypesAreCovered() {
        assertThat(transitionRejections().count())
                .as("all ten sibling transition-rejection types are asserted")
                .isEqualTo(10);
    }
}
