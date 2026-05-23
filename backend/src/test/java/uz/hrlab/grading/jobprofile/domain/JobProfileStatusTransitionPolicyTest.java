package uz.hrlab.grading.jobprofile.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("workflow")
class JobProfileStatusTransitionPolicyTest {

    private final JobProfileStatusTransitionPolicy policy =
            new JobProfileStatusTransitionPolicy();

    @ParameterizedTest
    @CsvSource({
            "DRAFT,SUBMIT_FOR_REVIEW",
            "DRAFT,ARCHIVE",
            "UNDER_REVIEW,APPROVE",
            "UNDER_REVIEW,REQUEST_CHANGES",
            "UNDER_REVIEW,ARCHIVE",
            "APPROVED,ARCHIVE",
            "APPROVED,CREATE_REVISION"
    })
    void validTransitionsArePermitted(JobProfileStatus from, JobProfileTransition action) {
        assertThat(policy.isAllowed(from, action)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT,APPROVE",
            "DRAFT,REQUEST_CHANGES",
            "DRAFT,CREATE_REVISION",
            "UNDER_REVIEW,SUBMIT_FOR_REVIEW",
            "UNDER_REVIEW,CREATE_REVISION",
            "APPROVED,SUBMIT_FOR_REVIEW",
            "APPROVED,APPROVE",
            "APPROVED,REQUEST_CHANGES",
            "ARCHIVED,SUBMIT_FOR_REVIEW",
            "ARCHIVED,APPROVE",
            "ARCHIVED,REQUEST_CHANGES",
            "ARCHIVED,ARCHIVE",
            "ARCHIVED,CREATE_REVISION"
    })
    void invalidTransitionsAreRejected(JobProfileStatus from, JobProfileTransition action) {
        assertThatThrownBy(() -> policy.check(from, action))
                .isInstanceOf(JobProfileTransitionRejectedException.class);
    }

    @Test
    void archivedIsTerminal() {
        for (JobProfileTransition action : JobProfileTransition.values()) {
            assertThat(policy.isAllowed(JobProfileStatus.ARCHIVED, action)).isFalse();
        }
    }
}
