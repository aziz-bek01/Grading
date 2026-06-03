package uz.hrlab.grading.methodology.domain;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Domain policy for {@link MethodologyVersion} state-machine validation
 * (architecture §9 + §14, ADR-002).
 *
 * <pre>
 *  DRAFT     → APPROVED            (APPROVE)
 *  DRAFT     → ARCHIVED            (ARCHIVE; reason required)
 *  APPROVED  → LOCKED              (LOCK; permission METHODOLOGY_LOCK)
 *  APPROVED  → ARCHIVED            (ARCHIVE; reason required)
 *  APPROVED  → (new DRAFT row)     (CREATE_NEW_VERSION — source unchanged)
 *  LOCKED    → ARCHIVED            (ARCHIVE; reason required)
 *  LOCKED    → (new DRAFT row)     (CREATE_NEW_VERSION — source unchanged)
 *  ARCHIVED  → ∅                   (terminal)
 * </pre>
 *
 * <p>APPROVED → APPROVED-edit is FORBIDDEN — see
 * {@link MethodologyVersionImmutabilityPolicy}.
 */
@Component
public class MethodologyVersionStatusTransitionPolicy {

    private static final Map<MethodologyVersionStatus,
            Set<MethodologyVersionTransition>> ALLOWED =
            new EnumMap<>(MethodologyVersionStatus.class);

    static {
        ALLOWED.put(MethodologyVersionStatus.DRAFT, EnumSet.of(
                MethodologyVersionTransition.APPROVE,
                MethodologyVersionTransition.ARCHIVE));
        ALLOWED.put(MethodologyVersionStatus.APPROVED, EnumSet.of(
                MethodologyVersionTransition.LOCK,
                MethodologyVersionTransition.ARCHIVE,
                MethodologyVersionTransition.CREATE_NEW_VERSION));
        ALLOWED.put(MethodologyVersionStatus.LOCKED, EnumSet.of(
                MethodologyVersionTransition.ARCHIVE,
                MethodologyVersionTransition.CREATE_NEW_VERSION));
        ALLOWED.put(MethodologyVersionStatus.ARCHIVED,
                EnumSet.noneOf(MethodologyVersionTransition.class));
    }

    public void check(MethodologyVersionStatus current,
                      MethodologyVersionTransition action) {
        Set<MethodologyVersionTransition> allowed = ALLOWED.getOrDefault(current,
                EnumSet.noneOf(MethodologyVersionTransition.class));
        if (!allowed.contains(action)) {
            throw new MethodologyVersionTransitionRejectedException(
                    "Cannot " + action.name() + " a methodology version in state "
                            + current.name());
        }
    }

    public boolean isAllowed(MethodologyVersionStatus current,
                             MethodologyVersionTransition action) {
        return ALLOWED.getOrDefault(current,
                        EnumSet.noneOf(MethodologyVersionTransition.class))
                .contains(action);
    }
}
