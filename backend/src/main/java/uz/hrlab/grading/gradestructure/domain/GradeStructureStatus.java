package uz.hrlab.grading.gradestructure.domain;

/** GradeStructure lifecycle (architecture §15.3 + ADR-002). */
public enum GradeStructureStatus {
    DRAFT,
    APPROVED,
    LOCKED,
    ARCHIVED
}
