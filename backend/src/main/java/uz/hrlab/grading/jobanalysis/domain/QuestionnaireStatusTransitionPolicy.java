package uz.hrlab.grading.jobanalysis.domain;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Domain policy for {@link QuestionnaireStatus} state-machine validation
 * (D-308 + F-301 — symmetry with {@code JobProfileStatusTransitionPolicy}).
 *
 * <pre>
 *   DRAFT       → IN_PROGRESS (START_ANSWERING)
 *   DRAFT       → COMPLETED   (SUBMIT)
 *   DRAFT       → ARCHIVED    (ARCHIVE; reason required)
 *   IN_PROGRESS → COMPLETED   (SUBMIT; required answered)
 *   IN_PROGRESS → ARCHIVED    (ARCHIVE; reason required)
 *   COMPLETED   → ARCHIVED    (ARCHIVE; reason required)
 *   ARCHIVED    → ∅           (terminal)
 * </pre>
 */
@Component
public class QuestionnaireStatusTransitionPolicy {

    private static final Map<QuestionnaireStatus, Set<QuestionnaireTransition>> ALLOWED =
            new EnumMap<>(QuestionnaireStatus.class);

    static {
        ALLOWED.put(QuestionnaireStatus.DRAFT, EnumSet.of(
                QuestionnaireTransition.START_ANSWERING,
                QuestionnaireTransition.SUBMIT,
                QuestionnaireTransition.ARCHIVE));
        ALLOWED.put(QuestionnaireStatus.IN_PROGRESS, EnumSet.of(
                QuestionnaireTransition.SUBMIT,
                QuestionnaireTransition.ARCHIVE));
        ALLOWED.put(QuestionnaireStatus.COMPLETED, EnumSet.of(
                QuestionnaireTransition.ARCHIVE));
        ALLOWED.put(QuestionnaireStatus.ARCHIVED,
                EnumSet.noneOf(QuestionnaireTransition.class));
    }

    /**
     * @throws QuestionnaireTransitionRejectedException if the transition is
     *         not permitted from the current state.
     */
    public void check(QuestionnaireStatus current, QuestionnaireTransition action) {
        Set<QuestionnaireTransition> allowed = ALLOWED.getOrDefault(current,
                EnumSet.noneOf(QuestionnaireTransition.class));
        if (!allowed.contains(action)) {
            throw new QuestionnaireTransitionRejectedException(
                    "QUESTIONNAIRE_TRANSITION_REJECTED",
                    "Cannot " + action.name() + " a questionnaire in state " + current.name());
        }
    }

    public boolean isAllowed(QuestionnaireStatus current, QuestionnaireTransition action) {
        return ALLOWED.getOrDefault(current,
                EnumSet.noneOf(QuestionnaireTransition.class)).contains(action);
    }
}
