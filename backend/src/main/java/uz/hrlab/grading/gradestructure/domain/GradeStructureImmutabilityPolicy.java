package uz.hrlab.grading.gradestructure.domain;

import org.springframework.stereotype.Component;

/**
 * Immutability gate: once a {@link GradeStructure} reaches APPROVED / LOCKED /
 * ARCHIVED, its grades + grade bands cannot be mutated. Edits require a fresh
 * DRAFT via CREATE_NEW_VERSION (deep-copy chain).
 *
 * <p>Service layer calls {@link #ensureMutable(GradeStructureStatus)} on every
 * write of {@code grades} / {@code grade_bands}. The DB trigger
 * {@code prevent_grade_changes_on_locked_structure} (014) is the defence-in-depth
 * companion.
 */
@Component
public class GradeStructureImmutabilityPolicy {

    public void ensureMutable(GradeStructureStatus status) {
        if (status != GradeStructureStatus.DRAFT) {
            throw new GradeStructureTransitionRejectedException(
                    "Cannot modify a grade structure in state " + status.name()
                            + " — call create-new-version first");
        }
    }
}
