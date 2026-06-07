package uz.hrlab.grading.jobprofile.domain;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Domain policy for {@link JobProfile} state-machine validation
 * (PRD §E6 + architecture §14).
 *
 * <pre>
 *   DRAFT        → UNDER_REVIEW  (SUBMIT_FOR_REVIEW)
 *   DRAFT        → ARCHIVED      (ARCHIVE; reason required)
 *   UNDER_REVIEW → APPROVED      (APPROVE — requires JOB_PROFILE_APPROVE)
 *   UNDER_REVIEW → DRAFT         (REQUEST_CHANGES; reason required)
 *   APPROVED     → ARCHIVED      (ARCHIVE; reason required)
 *   APPROVED     → ARCHIVED      (CREATE_REVISION — archive-on-revision)
 *   ARCHIVED     → ∅             (terminal)
 * </pre>
 *
 * <p>Approved profile EDIT is forbidden — see
 * {@link JobProfileImmutabilityPolicy}. CREATE_REVISION is permitted only from
 * APPROVED and is implemented as <b>archive-on-revision</b>: the source row is
 * moved APPROVED → ARCHIVED and a separate DRAFT profile is created with
 * {@code revisionNumber+1} and {@code previousRevisionId = source.id}. This
 * keeps at most one non-ARCHIVED profile per position, satisfying the partial
 * unique index without a migration.
 */
@Component
public class JobProfileStatusTransitionPolicy {

    private static final Map<JobProfileStatus, Set<JobProfileTransition>> ALLOWED =
            new EnumMap<>(JobProfileStatus.class);

    static {
        ALLOWED.put(JobProfileStatus.DRAFT, EnumSet.of(
                JobProfileTransition.SUBMIT_FOR_REVIEW,
                JobProfileTransition.ARCHIVE));
        ALLOWED.put(JobProfileStatus.UNDER_REVIEW, EnumSet.of(
                JobProfileTransition.APPROVE,
                JobProfileTransition.REQUEST_CHANGES,
                JobProfileTransition.ARCHIVE));
        ALLOWED.put(JobProfileStatus.APPROVED, EnumSet.of(
                JobProfileTransition.ARCHIVE,
                JobProfileTransition.CREATE_REVISION));
        ALLOWED.put(JobProfileStatus.ARCHIVED, EnumSet.noneOf(JobProfileTransition.class));
    }

    /**
     * @throws JobProfileTransitionRejectedException if the transition is not
     *         permitted from the current state.
     */
    public void check(JobProfileStatus current, JobProfileTransition action) {
        Set<JobProfileTransition> allowed = ALLOWED.getOrDefault(current,
                EnumSet.noneOf(JobProfileTransition.class));
        if (!allowed.contains(action)) {
            throw new JobProfileTransitionRejectedException(
                    "Cannot " + action.name() + " a profile in state " + current.name());
        }
    }

    /** True if the action is permitted from this state. */
    public boolean isAllowed(JobProfileStatus current, JobProfileTransition action) {
        return ALLOWED.getOrDefault(current, EnumSet.noneOf(JobProfileTransition.class))
                .contains(action);
    }
}
