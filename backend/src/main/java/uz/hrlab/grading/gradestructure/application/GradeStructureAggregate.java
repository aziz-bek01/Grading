package uz.hrlab.grading.gradestructure.application;

import uz.hrlab.grading.gradestructure.domain.Grade;
import uz.hrlab.grading.gradestructure.domain.GradeBand;
import uz.hrlab.grading.gradestructure.domain.GradeStructure;

import java.util.List;

/**
 * Read-side aggregate: the structure + all its grades + all its bands.
 * Used by GET /grade-structures/{id} (full detail page).
 */
public record GradeStructureAggregate(
        GradeStructure structure,
        List<Grade> grades,
        List<GradeBand> bands
) { }
