package uz.hrlab.grading.methodology.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("workflow")
class MethodologyVersionImmutabilityPolicyTest {

    private final MethodologyVersionImmutabilityPolicy policy =
            new MethodologyVersionImmutabilityPolicy();

    @Test
    void draftIsMutable() {
        assertThatCode(() -> policy.ensureMutable(MethodologyVersionStatus.DRAFT))
                .doesNotThrowAnyException();
    }

    @Test
    void approvedIsImmutable() {
        assertThatThrownBy(() -> policy.ensureMutable(MethodologyVersionStatus.APPROVED))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    void lockedIsImmutable() {
        assertThatThrownBy(() -> policy.ensureMutable(MethodologyVersionStatus.LOCKED))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class)
                .hasMessageContaining("LOCKED");
    }

    @Test
    void archivedIsImmutable() {
        assertThatThrownBy(() -> policy.ensureMutable(MethodologyVersionStatus.ARCHIVED))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
    }
}
