package uz.hrlab.grading.evaluation.domain;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Domain policy for {@link EvaluationStatus} state-machine validation
 * (architecture §14 + §15, PRD Phase 5 acceptance criteria).
 *
 * <pre>
 *  DRAFT          → COMPLETE / INCOMPLETE   (RECOMPUTE_COMPLETENESS)
 *  DRAFT          → ARCHIVED                (ARCHIVE; reason required)
 *  INCOMPLETE     → COMPLETE / INCOMPLETE   (RECOMPUTE_COMPLETENESS — toggles)
 *  INCOMPLETE     → ARCHIVED                (ARCHIVE; reason required)
 *  COMPLETE       → SUBMITTED               (SUBMIT)
 *  COMPLETE       → INCOMPLETE              (RECOMPUTE_COMPLETENESS — score removed)
 *  COMPLETE       → ARCHIVED                (ARCHIVE)
 *  SUBMITTED      → APPROVED                (APPROVE; permission EVALUATION_APPROVE)
 *  SUBMITTED      → COMPLETE                (REQUEST_CHANGES; reason required)
 *  SUBMITTED      → ARCHIVED                (ARCHIVE)
 *  APPROVED       → LOCKED                  (LOCK; permission EVALUATION_LOCK)
 *  APPROVED       → ARCHIVED                (ARCHIVE; reason required)
 *  LOCKED         → ARCHIVED                (ARCHIVE; reason required)
 *  ARCHIVED       → ∅                       (terminal)
 * </pre>
 *
 * <p>APPROVED + LOCKED edit of scores is FORBIDDEN — must use the calibration
 * use case (CALIBRATION_EDIT permission + reason). See
 * {@link EvaluationImmutabilityPolicy}.
 */
@Component
public class EvaluationStatusTransitionPolicy {

    private static final Map<EvaluationStatus, Set<EvaluationTransition>> ALLOWED =
            new EnumMap<>(EvaluationStatus.class);

    static {
        ALLOWED.put(EvaluationStatus.DRAFT, EnumSet.of(
                EvaluationTransition.RECOMPUTE_COMPLETENESS,
                EvaluationTransition.ARCHIVE));
        ALLOWED.put(EvaluationStatus.INCOMPLETE, EnumSet.of(
                EvaluationTransition.RECOMPUTE_COMPLETENESS,
                EvaluationTransition.ARCHIVE));
        ALLOWED.put(EvaluationStatus.COMPLETE, EnumSet.of(
                EvaluationTransition.RECOMPUTE_COMPLETENESS,
                EvaluationTransition.SUBMIT,
                EvaluationTransition.ARCHIVE));
        ALLOWED.put(EvaluationStatus.SUBMITTED, EnumSet.of(
                EvaluationTransition.APPROVE,
                EvaluationTransition.REQUEST_CHANGES,
                EvaluationTransition.ARCHIVE));
        ALLOWED.put(EvaluationStatus.APPROVED, EnumSet.of(
                EvaluationTransition.LOCK,
                EvaluationTransition.ARCHIVE));
        ALLOWED.put(EvaluationStatus.LOCKED, EnumSet.of(
                EvaluationTransition.ARCHIVE));
        ALLOWED.put(EvaluationStatus.ARCHIVED,
                EnumSet.noneOf(EvaluationTransition.class));
    }

    public void check(EvaluationStatus current, EvaluationTransition action) {
        Set<EvaluationTransition> allowed = ALLOWED.getOrDefault(current,
                EnumSet.noneOf(EvaluationTransition.class));
        if (!allowed.contains(action)) {
            throw new EvaluationTransitionRejectedException(
                    "Cannot " + action.name() + " an evaluation in state " + current.name());
        }
    }

    public boolean isAllowed(EvaluationStatus current, EvaluationTransition action) {
        return ALLOWED.getOrDefault(current,
                        EnumSet.noneOf(EvaluationTransition.class))
                .contains(action);
    }
}
