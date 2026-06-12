package uz.hrlab.grading.evaluation.domain;

import java.util.Set;

/**
 * Role an evaluator plays on a panel (MVP2 — multi-evaluator, DB proposal §2.2 +
 * UX proposal §2). The three {@link #MANDATORY_ROLES} must all be present before
 * a roster can be locked (server-side rule, enforced in LockRosterUseCase —
 * REQ-AVG-5). {@link #ADDITIONAL} covers the variable count of extra evaluators.
 */
public enum EvaluatorRole {
    HR_DIRECTOR,
    DEPARTMENT_DIRECTOR,
    EXTERNAL_EXPERT,
    ADDITIONAL;

    /**
     * The mandatory trio that every panel must include before its roster can be
     * locked and scoring may start (product rule: minimum 3 named roles).
     */
    public static final Set<EvaluatorRole> MANDATORY_ROLES =
            Set.of(HR_DIRECTOR, DEPARTMENT_DIRECTOR, EXTERNAL_EXPERT);
}
