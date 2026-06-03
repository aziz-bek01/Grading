package uz.hrlab.grading.methodology.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("workflow")
class MethodologyVersionStatusTransitionPolicyTest {

    private final MethodologyVersionStatusTransitionPolicy policy =
            new MethodologyVersionStatusTransitionPolicy();

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
    void validTransitionsArePermitted(MethodologyVersionStatus from,
                                      MethodologyVersionTransition action) {
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
    void invalidTransitionsAreRejected(MethodologyVersionStatus from,
                                       MethodologyVersionTransition action) {
        assertThatThrownBy(() -> policy.check(from, action))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
    }

    @Test
    void archivedIsTerminal() {
        for (MethodologyVersionTransition action : MethodologyVersionTransition.values()) {
            assertThat(policy.isAllowed(MethodologyVersionStatus.ARCHIVED, action)).isFalse();
        }
    }

    @Test
    void approvedToApprovedDirectEditForbidden() {
        // Editing an APPROVED version means it must be cloned, not edited
        assertThat(policy.isAllowed(MethodologyVersionStatus.APPROVED,
                MethodologyVersionTransition.APPROVE)).isFalse();
    }
}
