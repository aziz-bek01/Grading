package uz.hrlab.grading.gradestructure.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("workflow")
class GradeStructureStatusTransitionPolicyTest {

    private final GradeStructureStatusTransitionPolicy policy =
            new GradeStructureStatusTransitionPolicy();

    @ParameterizedTest
    @CsvSource({
            "DRAFT,APPROVE",
            "DRAFT,ARCHIVE",
            "APPROVED,LOCK",
            "APPROVED,ARCHIVE",
            "APPROVED,CREATE_NEW_VERSION",
            "LOCKED,ARCHIVE",
            "LOCKED,CREATE_NEW_VERSION"
    })
    void validTransitionsArePermitted(GradeStructureStatus from,
                                      GradeStructureTransition action) {
        assertThat(policy.isAllowed(from, action)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT,LOCK",
            "DRAFT,CREATE_NEW_VERSION",
            "APPROVED,APPROVE",
            "LOCKED,APPROVE",
            "LOCKED,LOCK",
            "ARCHIVED,APPROVE",
            "ARCHIVED,LOCK",
            "ARCHIVED,ARCHIVE",
            "ARCHIVED,CREATE_NEW_VERSION"
    })
    void invalidTransitionsAreRejected(GradeStructureStatus from,
                                       GradeStructureTransition action) {
        assertThatThrownBy(() -> policy.check(from, action))
                .isInstanceOf(GradeStructureTransitionRejectedException.class);
    }

    @Test
    void archivedIsTerminal() {
        for (GradeStructureTransition a : GradeStructureTransition.values()) {
            assertThat(policy.isAllowed(GradeStructureStatus.ARCHIVED, a)).isFalse();
        }
    }
}
