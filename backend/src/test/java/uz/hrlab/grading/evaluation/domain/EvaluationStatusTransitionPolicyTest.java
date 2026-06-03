package uz.hrlab.grading.evaluation.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("workflow")
class EvaluationStatusTransitionPolicyTest {

    private final EvaluationStatusTransitionPolicy policy = new EvaluationStatusTransitionPolicy();

    @ParameterizedTest
    @CsvSource({
            "DRAFT,RECOMPUTE_COMPLETENESS",
            "DRAFT,ARCHIVE",
            "INCOMPLETE,RECOMPUTE_COMPLETENESS",
            "INCOMPLETE,ARCHIVE",
            "COMPLETE,SUBMIT",
            "COMPLETE,RECOMPUTE_COMPLETENESS",
            "COMPLETE,ARCHIVE",
            "SUBMITTED,APPROVE",
            "SUBMITTED,REQUEST_CHANGES",
            "SUBMITTED,ARCHIVE",
            "APPROVED,LOCK",
            "APPROVED,ARCHIVE",
            "LOCKED,ARCHIVE"
    })
    void validTransitionsArePermitted(EvaluationStatus from, EvaluationTransition action) {
        assertThat(policy.isAllowed(from, action)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT,SUBMIT",
            "DRAFT,APPROVE",
            "DRAFT,LOCK",
            "INCOMPLETE,SUBMIT",
            "INCOMPLETE,APPROVE",
            "INCOMPLETE,LOCK",
            "COMPLETE,APPROVE",
            "COMPLETE,LOCK",
            "SUBMITTED,LOCK",
            "SUBMITTED,SUBMIT",
            "APPROVED,SUBMIT",
            "APPROVED,APPROVE",
            "APPROVED,REQUEST_CHANGES",
            "LOCKED,APPROVE",
            "LOCKED,LOCK",
            "LOCKED,SUBMIT",
            "ARCHIVED,APPROVE",
            "ARCHIVED,LOCK",
            "ARCHIVED,SUBMIT",
            "ARCHIVED,ARCHIVE"
    })
    void invalidTransitionsAreRejected(EvaluationStatus from, EvaluationTransition action) {
        assertThatThrownBy(() -> policy.check(from, action))
                .isInstanceOf(EvaluationTransitionRejectedException.class);
    }

    @Test
    void archivedIsTerminal() {
        for (EvaluationTransition t : EvaluationTransition.values()) {
            assertThat(policy.isAllowed(EvaluationStatus.ARCHIVED, t)).isFalse();
        }
    }

    @Test
    void lockedCanOnlyGoToArchived() {
        for (EvaluationTransition t : EvaluationTransition.values()) {
            if (t == EvaluationTransition.ARCHIVE) continue;
            assertThat(policy.isAllowed(EvaluationStatus.LOCKED, t)).isFalse();
        }
        assertThat(policy.isAllowed(EvaluationStatus.LOCKED, EvaluationTransition.ARCHIVE)).isTrue();
    }
}
