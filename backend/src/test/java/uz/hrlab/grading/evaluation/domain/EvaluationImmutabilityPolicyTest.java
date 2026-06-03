package uz.hrlab.grading.evaluation.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("workflow")
class EvaluationImmutabilityPolicyTest {

    private final EvaluationImmutabilityPolicy policy = new EvaluationImmutabilityPolicy();

    @Test
    void editAllowedOnDraftIncompleteCompleteSubmitted() {
        assertThatCode(() -> policy.enforceCanEdit(EvaluationStatus.DRAFT)).doesNotThrowAnyException();
        assertThatCode(() -> policy.enforceCanEdit(EvaluationStatus.INCOMPLETE)).doesNotThrowAnyException();
        assertThatCode(() -> policy.enforceCanEdit(EvaluationStatus.COMPLETE)).doesNotThrowAnyException();
        assertThatCode(() -> policy.enforceCanEdit(EvaluationStatus.SUBMITTED)).doesNotThrowAnyException();
    }

    @Test
    void editForbiddenOnApprovedLockedArchived() {
        assertThatThrownBy(() -> policy.enforceCanEdit(EvaluationStatus.APPROVED))
                .isInstanceOf(EvaluationTransitionRejectedException.class);
        assertThatThrownBy(() -> policy.enforceCanEdit(EvaluationStatus.LOCKED))
                .isInstanceOf(EvaluationTransitionRejectedException.class);
        assertThatThrownBy(() -> policy.enforceCanEdit(EvaluationStatus.ARCHIVED))
                .isInstanceOf(EvaluationTransitionRejectedException.class);
    }

    @Test
    void calibrationAllowedOnSubmittedAndApprovedOnly() {
        assertThatCode(() -> policy.enforceCanCalibrate(EvaluationStatus.SUBMITTED))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.enforceCanCalibrate(EvaluationStatus.APPROVED))
                .doesNotThrowAnyException();
    }

    @Test
    void calibrationForbiddenOnOtherStates() {
        for (EvaluationStatus s : EvaluationStatus.values()) {
            if (s == EvaluationStatus.SUBMITTED || s == EvaluationStatus.APPROVED) continue;
            assertThatThrownBy(() -> policy.enforceCanCalibrate(s))
                    .isInstanceOf(EvaluationTransitionRejectedException.class);
        }
    }

    @Test
    void enumCoversAllStates() {
        // Sanity — if a new state is added, this test forces a review.
        assertThat(EvaluationStatus.values()).hasSize(7);
    }
}
