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
    void allFifteenStatusesExist() {
        // 13 blueprint + ARCHIVED retention + Batch-4 DEAD_LETTER.
        assertThat(ImportBatchStatus.values()).hasSize(15);
    }

    // --- Batch-4 bounded-retry + dead-letter FSM ---

    @Test
    void failedIsRetryableBackToScanningOrDeadLetter() {
        // Transient processing failure rests in FAILED; the re-queuer restarts
        // the pipeline (FAILED -> SCANNING) or, when exhausted, dead-letters.
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.FAILED, ImportBatchStatus.SCANNING)).isTrue();
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.FAILED, ImportBatchStatus.DEAD_LETTER)).isTrue();
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.FAILED, ImportBatchStatus.ARCHIVED)).isTrue();
    }

    @Test
    void validationFailedIsNotAutoRetried() {
        // VALIDATION_FAILED is a deterministic user-data outcome — it must NOT
        // be able to restart the pipeline (no FAILED/SCANNING/DEAD_LETTER edge).
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.VALIDATION_FAILED, ImportBatchStatus.SCANNING)).isFalse();
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.VALIDATION_FAILED, ImportBatchStatus.FAILED)).isFalse();
        assertThat(ImportBatchStatusTransitionPolicy.isAllowed(
                ImportBatchStatus.VALIDATION_FAILED, ImportBatchStatus.DEAD_LETTER)).isFalse();
    }

    @Test
    void deadLetterIsNeverReDispatched() {
        assertThat(ImportBatchStatusTransitionPolicy.allowedTargets(ImportBatchStatus.DEAD_LETTER)).isEmpty();
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
