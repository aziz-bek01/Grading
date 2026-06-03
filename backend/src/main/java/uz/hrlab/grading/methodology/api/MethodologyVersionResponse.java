package uz.hrlab.grading.methodology.api;

import uz.hrlab.grading.methodology.domain.MethodologyVersion;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.domain.ScoringMode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Methodology version DTO.
 *
 * <p>{@code lockedByName} / {@code approvedByName} are resolved server-side
 * from {@code public.users.full_name} (defect D-407 / F-411). Frontend uses
 * the name in the LockedMethodologyHeader; if backend returns {@code null}
 * the FE falls back to a localised "Unknown actor" label.
 */
public record MethodologyVersionResponse(
        UUID id,
        UUID methodologyId,
        int versionNumber,
        MethodologyVersionStatus status,
        ScoringMode scoringMode,
        BigDecimal targetTotalPoints,
        UUID previousVersionId,
        OffsetDateTime approvedAt,
        UUID approvedBy,
        String approvedByName,
        OffsetDateTime lockedAt,
        UUID lockedBy,
        String lockedByName
) {
    /** Build a response without resolved actor names (legacy callers). */
    public static MethodologyVersionResponse from(MethodologyVersion v) {
        return from(v, null, null);
    }

    /** Build a response with resolved actor names (D-407). */
    public static MethodologyVersionResponse from(MethodologyVersion v,
                                                  String approvedByName,
                                                  String lockedByName) {
        return new MethodologyVersionResponse(v.id(), v.methodologyId(),
                v.versionNumber(), v.status(), v.scoringMode(),
                v.targetTotalPoints(), v.previousVersionId(),
                v.approvedAt(), v.approvedBy(), approvedByName,
                v.lockedAt(), v.lockedBy(), lockedByName);
    }
}
