package uz.hrlab.grading.gradestructure.api;

import uz.hrlab.grading.gradestructure.domain.GradeBandLookupResult;

import java.util.UUID;

public record PreviewLookupResponse(boolean matched, UUID gradeBandId, UUID gradeId,
                                     Integer gradeNumber) {
    public static PreviewLookupResponse miss() {
        return new PreviewLookupResponse(false, null, null, null);
    }

    public static PreviewLookupResponse of(GradeBandLookupResult r) {
        return new PreviewLookupResponse(true, r.gradeBandId(), r.gradeId(), r.gradeNumber());
    }
}
