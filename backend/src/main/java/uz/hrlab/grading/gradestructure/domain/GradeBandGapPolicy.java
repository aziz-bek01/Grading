package uz.hrlab.grading.gradestructure.domain;

/**
 * How gaps between adjacent grade bands are handled at approval time.
 *
 * <ul>
 *   <li>{@link #STRICT_NO_GAPS} — bands must form a contiguous range; gaps
 *       reject the approval.</li>
 *   <li>{@link #ALLOW_GAPS_WARN} — gaps are logged but the approval succeeds;
 *       evaluations whose score falls into a gap get a null grade assignment.</li>
 * </ul>
 */
public enum GradeBandGapPolicy {
    STRICT_NO_GAPS,
    ALLOW_GAPS_WARN
}
