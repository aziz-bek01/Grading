package uz.hrlab.grading.gradestructure.domain;

import uz.hrlab.grading.common.exception.BaseDomainException;

/**
 * Thrown when a {@link GradeStructure} state transition violates the
 * {@link GradeStructureStatusTransitionPolicy} or content rules (overlap, gap,
 * empty grades, ...). Mapped to HTTP 409 by the global exception handler.
 */
public class GradeStructureTransitionRejectedException extends BaseDomainException {
    public GradeStructureTransitionRejectedException(String message) {
        super("GRADE_STRUCTURE_TRANSITION_REJECTED", message);
    }
}
