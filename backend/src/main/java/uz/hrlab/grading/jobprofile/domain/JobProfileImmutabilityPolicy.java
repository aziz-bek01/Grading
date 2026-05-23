package uz.hrlab.grading.jobprofile.domain;

import org.springframework.stereotype.Component;

/**
 * Immutability guard for {@link JobProfile} (PRD §E6 hard rule: an APPROVED
 * profile cannot be edited — any change requires a new revision; ARCHIVED is
 * terminal).
 */
@Component
public class JobProfileImmutabilityPolicy {

    /**
     * Block UPDATE / DELETE on APPROVED or ARCHIVED profiles.
     *
     * @throws JobProfileTransitionRejectedException with code
     *         {@code JOB_PROFILE_LOCKED} when the profile is APPROVED or ARCHIVED.
     */
    public void enforceMutable(JobProfileStatus current) {
        if (current == JobProfileStatus.APPROVED || current == JobProfileStatus.ARCHIVED) {
            throw new JobProfileTransitionRejectedException(
                    "JOB_PROFILE_LOCKED",
                    "Profile is " + current.name()
                            + "; edits forbidden — create a revision instead");
        }
    }

    /**
     * Block edits to a UNDER_REVIEW profile (only allowed transitions are
     * APPROVE / REQUEST_CHANGES / ARCHIVE, not content edits).
     */
    public void enforceEditable(JobProfileStatus current) {
        if (current != JobProfileStatus.DRAFT) {
            throw new JobProfileTransitionRejectedException(
                    "JOB_PROFILE_NOT_EDITABLE",
                    "Profile is " + current.name() + "; only DRAFT profiles may be edited");
        }
    }
}
