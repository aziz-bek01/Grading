package uz.hrlab.grading.gradestructure.api;

import uz.hrlab.grading.gradestructure.domain.GradeBandLookupResult;

import java.util.UUID;

/**
 * Score → grade lookup preview result.
 *
 * <p>Carries BOTH wire shapes the consumers expect (BE-3): the existing
 * evaluation consumer reads {@code matched}; the grade page reads
 * {@code out_of_range}. {@code out_of_range} is the derived inverse of
 * {@code matched} so neither consumer breaks. Serializes snake_case via the
 * global Jackson strategy ({@code matched, out_of_range, grade_band_id,
 * grade_id, grade_number}).
 */
public record PreviewLookupResponse(boolean matched, boolean outOfRange,
                                    UUID gradeBandId, UUID gradeId,
                                    Integer gradeNumber) {
    public static PreviewLookupResponse miss() {
        return new PreviewLookupResponse(false, true, null, null, null);
    }

    public static PreviewLookupResponse of(GradeBandLookupResult r) {
        return new PreviewLookupResponse(true, false, r.gradeBandId(), r.gradeId(), r.gradeNumber());
    }
}
