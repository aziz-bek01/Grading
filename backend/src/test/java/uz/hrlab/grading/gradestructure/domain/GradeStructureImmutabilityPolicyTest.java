package uz.hrlab.grading.gradestructure.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("workflow")
class GradeStructureImmutabilityPolicyTest {

    private final GradeStructureImmutabilityPolicy policy =
            new GradeStructureImmutabilityPolicy();

    @Test
    void draftIsMutable() {
        assertThatCode(() -> policy.ensureMutable(GradeStructureStatus.DRAFT))
                .doesNotThrowAnyException();
    }

    @Test
    void approvedIsImmutable() {
        assertThatThrownBy(() -> policy.ensureMutable(GradeStructureStatus.APPROVED))
                .isInstanceOf(GradeStructureTransitionRejectedException.class);
    }

    @Test
    void lockedIsImmutable() {
        assertThatThrownBy(() -> policy.ensureMutable(GradeStructureStatus.LOCKED))
                .isInstanceOf(GradeStructureTransitionRejectedException.class);
    }

    @Test
    void archivedIsImmutable() {
        assertThatThrownBy(() -> policy.ensureMutable(GradeStructureStatus.ARCHIVED))
                .isInstanceOf(GradeStructureTransitionRejectedException.class);
    }
}
