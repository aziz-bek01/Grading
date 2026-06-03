package uz.hrlab.grading.evaluation.domain;

import org.springframework.stereotype.Component;

/**
 * Domain policy: APPROVED + LOCKED + ARCHIVED evaluations reject UPDATE/DELETE
 * on scores via the regular EDIT path. Calibration is the only legal write path
 * post-APPROVE — see CalibrateEvaluationScoreUseCase.
 *
 * <p>App-layer enforcement + DB trigger {@code trg_evaluation_score_lock}
 * (locked + archived only). APPROVED app-layer rejection guards the regular
 * EDIT path; the calibration use case bypasses this check by design.
 */
@Component
public class EvaluationImmutabilityPolicy {

    public void enforceCanEdit(EvaluationStatus current) {
        if (current == EvaluationStatus.APPROVED
                || current == EvaluationStatus.LOCKED
                || current == EvaluationStatus.ARCHIVED) {
            throw new EvaluationTransitionRejectedException(
                    "Evaluation in state " + current.name()
                            + " is immutable — use calibration to adjust scores");
        }
    }

    public void enforceCanCalibrate(EvaluationStatus current) {
        // Calibration is allowed on SUBMITTED + APPROVED only.
        // LOCKED / ARCHIVED — no further changes; DRAFT/INCOMPLETE/COMPLETE
        // — use the regular EDIT path.
        if (current != EvaluationStatus.SUBMITTED && current != EvaluationStatus.APPROVED) {
            throw new EvaluationTransitionRejectedException(
                    "Calibration is allowed only on SUBMITTED or APPROVED evaluations; "
                            + "current state is " + current.name());
        }
    }
}
