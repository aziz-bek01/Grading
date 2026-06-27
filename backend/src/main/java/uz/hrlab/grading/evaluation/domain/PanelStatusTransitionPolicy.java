package uz.hrlab.grading.evaluation.domain;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Domain policy for the {@link EvaluationPanel} state machine (MVP2 —
 * multi-evaluator). Single source of truth for which {@code (current →
 * transition)} pairs are legal.
 *
 * <pre>
 *  COLLECTING            → AWAITING_EVALUATIONS  (LOCK_ROSTER)
 *  AWAITING_EVALUATIONS  → AVERAGED              (AVERAGE)
 *  AWAITING_EVALUATIONS  → ARCHIVED              (ARCHIVE)
 *  AVERAGED              → SUBMITTED             (SUBMIT_TO_CEO)
 *  AVERAGED              → AWAITING_EVALUATIONS  (REOPEN_TO_AWAITING — non-destructive)
 *  AVERAGED              → ARCHIVED              (ARCHIVE)
 *  SUBMITTED             → APPROVED              (APPROVE)
 *  SUBMITTED             → AWAITING_EVALUATIONS  (REOPEN_TO_AWAITING — CHANGES_REQUESTED, scores kept)
 *  SUBMITTED             → COLLECTING            (RESET_TO_COLLECTING — REJECT, scores DELETED)
 *  SUBMITTED             → ARCHIVED              (ARCHIVE)
 *  APPROVED / LOCKED / ARCHIVED → ∅              (committed/terminal here)
 * </pre>
 *
 * <p>{@link PanelStatusTransition#RESET_TO_COLLECTING} is the destructive
 * REJECT path: the product owner explicitly confirmed that a CEO reject sends
 * the panel back to "Сбор экспертов" (COLLECTING), DELETING every evaluator's
 * sheet + scores. It is distinct from the non-destructive
 * {@link PanelStatusTransition#REOPEN_TO_AWAITING} re-review path.
 *
 * <p>This policy is additive: it does NOT retrofit the existing ad-hoc status
 * guards in {@code ApprovePanelUseCase} / {@code ArchivePanelUseCase} /
 * {@code LockRosterUseCase}; it is consulted by
 * {@code ResetPanelToCollectingUseCase} (and is available for the others to
 * adopt later).
 */
@Component
public class PanelStatusTransitionPolicy {

    private static final Map<EvaluationPanelStatus,
            Set<PanelStatusTransition>> ALLOWED =
            new EnumMap<>(EvaluationPanelStatus.class);

    static {
        ALLOWED.put(EvaluationPanelStatus.COLLECTING, EnumSet.of(
                PanelStatusTransition.LOCK_ROSTER));
        ALLOWED.put(EvaluationPanelStatus.AWAITING_EVALUATIONS, EnumSet.of(
                PanelStatusTransition.AVERAGE,
                PanelStatusTransition.ARCHIVE));
        ALLOWED.put(EvaluationPanelStatus.AVERAGED, EnumSet.of(
                PanelStatusTransition.SUBMIT_TO_CEO,
                PanelStatusTransition.REOPEN_TO_AWAITING,
                PanelStatusTransition.ARCHIVE));
        ALLOWED.put(EvaluationPanelStatus.SUBMITTED, EnumSet.of(
                PanelStatusTransition.APPROVE,
                PanelStatusTransition.REOPEN_TO_AWAITING,
                // System-triggered destructive reject (product-confirmed) — the
                // transition this change exists to enable.
                PanelStatusTransition.RESET_TO_COLLECTING,
                PanelStatusTransition.ARCHIVE));
        ALLOWED.put(EvaluationPanelStatus.APPROVED,
                EnumSet.noneOf(PanelStatusTransition.class));
        ALLOWED.put(EvaluationPanelStatus.LOCKED,
                EnumSet.noneOf(PanelStatusTransition.class));
        ALLOWED.put(EvaluationPanelStatus.ARCHIVED,
                EnumSet.noneOf(PanelStatusTransition.class));
    }

    public boolean isAllowed(EvaluationPanelStatus current, PanelStatusTransition action) {
        return ALLOWED.getOrDefault(current, EnumSet.noneOf(PanelStatusTransition.class))
                .contains(action);
    }

    public void check(EvaluationPanelStatus current, PanelStatusTransition action) {
        if (!isAllowed(current, action)) {
            throw new PanelStatusTransitionRejectedException(
                    "Cannot " + action.name() + " a panel in state " + current.name());
        }
    }
}
