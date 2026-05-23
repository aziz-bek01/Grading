package uz.hrlab.grading.jobprofile.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("workflow")
class JobProfileImmutabilityPolicyTest {

    private final JobProfileImmutabilityPolicy policy = new JobProfileImmutabilityPolicy();

    @Test
    void draftIsEditableAndMutable() {
        assertThatCode(() -> policy.enforceEditable(JobProfileStatus.DRAFT))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.enforceMutable(JobProfileStatus.DRAFT))
                .doesNotThrowAnyException();
    }

    @Test
    void underReviewIsNotEditable() {
        assertThatThrownBy(() -> policy.enforceEditable(JobProfileStatus.UNDER_REVIEW))
                .isInstanceOf(JobProfileTransitionRejectedException.class)
                .hasMessageContaining("UNDER_REVIEW");
    }

    @Test
    void approvedIsImmutable() {
        assertThatThrownBy(() -> policy.enforceMutable(JobProfileStatus.APPROVED))
                .isInstanceOf(JobProfileTransitionRejectedException.class)
                .hasMessageContaining("APPROVED");
        assertThatThrownBy(() -> policy.enforceEditable(JobProfileStatus.APPROVED))
                .isInstanceOf(JobProfileTransitionRejectedException.class);
    }

    @Test
    void archivedIsImmutable() {
        assertThatThrownBy(() -> policy.enforceMutable(JobProfileStatus.ARCHIVED))
                .isInstanceOf(JobProfileTransitionRejectedException.class);
        assertThatThrownBy(() -> policy.enforceEditable(JobProfileStatus.ARCHIVED))
                .isInstanceOf(JobProfileTransitionRejectedException.class);
    }
}
