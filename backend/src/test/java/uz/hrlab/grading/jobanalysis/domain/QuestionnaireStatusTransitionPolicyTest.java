package uz.hrlab.grading.jobanalysis.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Locks the {@link QuestionnaireStatusTransitionPolicy} matrix (D-308 / F-301).
 *
 * <p>4 states × 3 actions = 12 cells. 6 valid transitions + 6 invalid + plus
 * an "archived is terminal" sweep.
 */
@Tag("workflow")
class QuestionnaireStatusTransitionPolicyTest {

    private final QuestionnaireStatusTransitionPolicy policy =
            new QuestionnaireStatusTransitionPolicy();

    @ParameterizedTest
    @CsvSource({
            "DRAFT,START_ANSWERING",
            "DRAFT,SUBMIT",
            "DRAFT,ARCHIVE",
            "IN_PROGRESS,SUBMIT",
            "IN_PROGRESS,ARCHIVE",
            "COMPLETED,ARCHIVE"
    })
    void validTransitionsArePermitted(QuestionnaireStatus from,
                                      QuestionnaireTransition action) {
        assertThat(policy.isAllowed(from, action)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "IN_PROGRESS,START_ANSWERING",
            "COMPLETED,START_ANSWERING",
            "COMPLETED,SUBMIT",
            "ARCHIVED,START_ANSWERING",
            "ARCHIVED,SUBMIT",
            "ARCHIVED,ARCHIVE"
    })
    void invalidTransitionsAreRejected(QuestionnaireStatus from,
                                       QuestionnaireTransition action) {
        assertThatThrownBy(() -> policy.check(from, action))
                .isInstanceOf(QuestionnaireTransitionRejectedException.class);
    }

    @Test
    void archivedIsTerminal() {
        for (QuestionnaireTransition action : QuestionnaireTransition.values()) {
            assertThat(policy.isAllowed(QuestionnaireStatus.ARCHIVED, action)).isFalse();
        }
    }
}
