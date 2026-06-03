package uz.hrlab.grading.gradestructure.domain;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Domain policy for the {@link GradeStructure} state machine.
 *
 * <pre>
 *  DRAFT     → APPROVED              (APPROVE)
 *  DRAFT     → ARCHIVED              (ARCHIVE; reason)
 *  APPROVED  → LOCKED                (LOCK; permission GRADE_STRUCTURE_LOCK)
 *  APPROVED  → ARCHIVED              (ARCHIVE; reason)
 *  APPROVED  → (new DRAFT row)       (CREATE_NEW_VERSION — source unchanged)
 *  LOCKED    → ARCHIVED              (ARCHIVE; reason)
 *  LOCKED    → (new DRAFT row)       (CREATE_NEW_VERSION — source unchanged)
 *  ARCHIVED  → ∅                     (terminal)
 * </pre>
 *
 * APPROVED → APPROVED-edit is FORBIDDEN — see
 * {@link GradeStructureImmutabilityPolicy}.
 */
@Component
public class GradeStructureStatusTransitionPolicy {

    private static final Map<GradeStructureStatus,
            Set<GradeStructureTransition>> ALLOWED =
            new EnumMap<>(GradeStructureStatus.class);

    static {
        ALLOWED.put(GradeStructureStatus.DRAFT, EnumSet.of(
                GradeStructureTransition.APPROVE,
                GradeStructureTransition.ARCHIVE));
        ALLOWED.put(GradeStructureStatus.APPROVED, EnumSet.of(
                GradeStructureTransition.LOCK,
                GradeStructureTransition.ARCHIVE,
                GradeStructureTransition.CREATE_NEW_VERSION));
        ALLOWED.put(GradeStructureStatus.LOCKED, EnumSet.of(
                GradeStructureTransition.ARCHIVE,
                GradeStructureTransition.CREATE_NEW_VERSION));
        ALLOWED.put(GradeStructureStatus.ARCHIVED,
                EnumSet.noneOf(GradeStructureTransition.class));
    }

    public void check(GradeStructureStatus current, GradeStructureTransition action) {
        Set<GradeStructureTransition> allowed = ALLOWED.getOrDefault(current,
                EnumSet.noneOf(GradeStructureTransition.class));
        if (!allowed.contains(action)) {
            throw new GradeStructureTransitionRejectedException(
                    "Cannot " + action.name() + " a grade structure in state "
                            + current.name());
        }
    }

    public boolean isAllowed(GradeStructureStatus current, GradeStructureTransition action) {
        return ALLOWED.getOrDefault(current,
                EnumSet.noneOf(GradeStructureTransition.class))
                .contains(action);
    }
}
