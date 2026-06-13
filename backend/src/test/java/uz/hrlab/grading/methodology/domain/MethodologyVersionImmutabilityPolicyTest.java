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

    // --- BE-2: ensureMutableOrApprovedEdit status × permission matrix ---------

    @Test
    void approvedEdit_draftAlwaysAllowed_returnsFalse_noGuc() {
        // DRAFT path: allowed regardless of permission, NOT an approved edit.
        assertThatCode(() -> {
            boolean approvedEdit =
                    policy.ensureMutableOrApprovedEdit(MethodologyVersionStatus.DRAFT, false);
            org.assertj.core.api.Assertions.assertThat(approvedEdit).isFalse();
        }).doesNotThrowAnyException();
        boolean approvedEditWithPerm =
                policy.ensureMutableOrApprovedEdit(MethodologyVersionStatus.DRAFT, true);
        org.assertj.core.api.Assertions.assertThat(approvedEditWithPerm).isFalse();
    }

    @Test
    void approvedEdit_approvedWithPermission_allowed_returnsTrue() {
        boolean approvedEdit =
                policy.ensureMutableOrApprovedEdit(MethodologyVersionStatus.APPROVED, true);
        org.assertj.core.api.Assertions.assertThat(approvedEdit).isTrue();
    }

    @Test
    void approvedEdit_approvedWithoutPermission_rejected() {
        assertThatThrownBy(() ->
                policy.ensureMutableOrApprovedEdit(MethodologyVersionStatus.APPROVED, false))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class)
                .hasMessageContaining("METHODOLOGY_EDIT_APPROVED");
    }

    @Test
    void approvedEdit_lockedAlwaysRejected_evenWithPermission() {
        assertThatThrownBy(() ->
                policy.ensureMutableOrApprovedEdit(MethodologyVersionStatus.LOCKED, true))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class)
                .hasMessageContaining("LOCKED");
        assertThatThrownBy(() ->
                policy.ensureMutableOrApprovedEdit(MethodologyVersionStatus.LOCKED, false))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
    }

    @Test
    void approvedEdit_archivedAlwaysRejected_evenWithPermission() {
        assertThatThrownBy(() ->
                policy.ensureMutableOrApprovedEdit(MethodologyVersionStatus.ARCHIVED, true))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
        assertThatThrownBy(() ->
                policy.ensureMutableOrApprovedEdit(MethodologyVersionStatus.ARCHIVED, false))
                .isInstanceOf(MethodologyVersionTransitionRejectedException.class);
    }
}
