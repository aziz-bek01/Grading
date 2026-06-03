package uz.hrlab.grading.gradestructure.domain;

import java.util.UUID;

/**
 * Outcome of a score → grade lookup. Returned wrapped in an {@link java.util.Optional};
 * empty means the score is out of range or fell into a gap (caller decides
 * whether to leave the evaluation unassigned or to fail).
 */
public record GradeBandLookupResult(UUID gradeBandId, UUID gradeId, int gradeNumber) { }
