package uz.hrlab.grading.jobprofile.api;

import uz.hrlab.grading.jobprofile.domain.JobProfile;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatus;

import java.util.UUID;

/**
 * PAGE-2 BE — lean status projection for the bulk "job-profile status by
 * position ids" read ({@code GET /api/v1/job-profiles/statuses}). One element
 * per position that HAS an active (non-ARCHIVED) profile; positions without one
 * are simply ABSENT (the Positions Catalog treats absent as "no profile yet").
 *
 * <p>Deliberately NOT the full {@link JobProfileResponse}: the catalog only
 * needs the status pill + a drill-down id, so the heavy multilingual long-text
 * fields are omitted. Reuses the {@link JobProfileStatus} enum and the existing
 * {@link JobProfile} domain accessors — no duplicated mapping.
 *
 * <p>The global Jackson SNAKE_CASE strategy renders the wire fields
 * {@code position_id}, {@code status}, {@code job_profile_id},
 * {@code revision_number}.
 */
public record PositionJobProfileStatusResponse(
        UUID positionId,
        JobProfileStatus status,
        UUID jobProfileId,
        int revisionNumber
) {
    public static PositionJobProfileStatusResponse from(JobProfile p) {
        return new PositionJobProfileStatusResponse(
                p.positionId(), p.status(), p.id(), p.revisionNumber());
    }
}
