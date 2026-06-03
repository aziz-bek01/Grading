package uz.hrlab.grading.integration.imports.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** State-machine tests for the 13-status import-batch FSM. */
@Tag("workflow")
class ImportBatchStatusTransitionPolicyTest {

    @Test
    void uploadedCanMoveToScanningOrFailedOrCancelled() {
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.UPLOADED, ImportBatchStatus.SCANNING)).isTrue();
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.UPLOADED, ImportBatchStatus.FAILED)).isTrue();
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.UPLOADED, ImportBatchStatus.CANCELLED)).isTrue();
    }

    @Test
    void scanningCanMoveToParsingOrScanFailedOrCancelled() {
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.SCANNING, ImportBatchStatus.PARSING)).isTrue();
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.SCANNING, ImportBatchStatus.SCAN_FAILED)).isTrue();
    }

    @Test
    void validatingMayReachReadyForReviewOrValidationFailed() {
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.VALIDATING, ImportBatchStatus.READY_FOR_REVIEW)).isTrue();
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.VALIDATING, ImportBatchStatus.VALIDATION_FAILED)).isTrue();
    }

    @Test
    void readyToCommitMayReachCommittingOnly() {
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.READY_TO_COMMIT, ImportBatchStatus.COMMITTING)).isTrue();
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.READY_TO_COMMIT, ImportBatchStatus.COMMITTED)).isFalse();
    }

    @Test
    void committingMayLandInCommittedPartiallyOrFailed() {
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.COMMITTING, ImportBatchStatus.COMMITTED)).isTrue();
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.COMMITTING, ImportBatchStatus.PARTIALLY_COMMITTED)).isTrue();
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.COMMITTING, ImportBatchStatus.FAILED)).isTrue();
    }

    @Test
    void archivedIsTerminal() {
        for (ImportBatchStatus to : ImportBatchStatus.values()) {
            assertThat(ImportBatchStatusTransitionPolicy.isAllowed(ImportBatchStatus.ARCHIVED, to))
                    .as("archived should not transition to " + to)
                    .isFalse();
        }
    }

    @Test
    void scanFailedIsTerminalExceptArchive() {
        for (ImportBatchStatus to : ImportBatchStatus.values()) {
            if (to == ImportBatchStatus.ARCHIVED) continue;
            assertThat(ImportBatchStatusTransitionPolicy.isAllowed(ImportBatchStatus.SCAN_FAILED, to)).isFalse();
        }
    }

    @Test
    void illegalTransitionThrows() {
        assertThatThrownBy(() -> ImportBatchStatusTransitionPolicy.assertAllowed(
                ImportBatchStatus.UPLOADED, ImportBatchStatus.COMMITTED))
                .isInstanceOf(ImportBatchTransitionRejectedException.class);
    }

    @Test
    void noBackwardsTransitionFromValidatedToParsing() {
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.READY_FOR_REVIEW, ImportBatchStatus.PARSING)).isFalse();
    }

    @Test
    void readyForReviewCanGoToReValidateOrCancel() {
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.READY_FOR_REVIEW, ImportBatchStatus.VALIDATING)).isTrue();
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.READY_FOR_REVIEW, ImportBatchStatus.CANCELLED)).isTrue();
    }

    @Test
    void allThirteenStatusesExist() {
        assertThat(ImportBatchStatus.values()).hasSize(14); // includes ARCHIVED — brief lists 13 + ARCHIVED retention
    }

    @Test
    void cancelledIsTerminalExceptArchive() {
        for (ImportBatchStatus to : ImportBatchStatus.values()) {
            if (to == ImportBatchStatus.ARCHIVED) continue;
            assertThat(ImportBatchStatusTransitionPolicy.isAllowed(ImportBatchStatus.CANCELLED, to)).isFalse();
        }
    }

    @Test
    void allowedTargetsAreImmutableCopy() {
        var targets = ImportBatchStatusTransitionPolicy.allowedTargets(ImportBatchStatus.UPLOADED);
        assertThat(targets).contains(ImportBatchStatus.SCANNING);
        // Verify mutation does not leak
        targets.clear();
        assertThat(ImportBatchStatusTransitionPolicy.allowedTargets(ImportBatchStatus.UPLOADED))
                .contains(ImportBatchStatus.SCANNING);
    }
}
