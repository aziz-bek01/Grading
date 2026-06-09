package uz.hrlab.grading.integration.imports.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.jobprofile.application.CreateJobProfileCommand;
import uz.hrlab.grading.jobprofile.application.CreateJobProfileUseCase;
import uz.hrlab.grading.jobprofile.domain.JobProfile;

/**
 * Per-row transactional boundary for the JOB_PROFILE_V1 importer.
 *
 * <p>{@link CommitImportBatchUseCase#commit} runs inside one {@code @Transactional}
 * batch transaction and is resilient per row: a single bad row is recorded as
 * FAILED while the rest continue. The reused
 * {@link CreateJobProfileUseCase#create} is {@code @Transactional} (REQUIRED);
 * if it joined the batch transaction directly, a domain rejection
 * (PROFILE_ALREADY_EXISTS, project locked, ABAC denial) would mark the SHARED
 * transaction rollback-only and poison the whole batch at commit time
 * (UnexpectedRollbackException) — breaking the "other rows continue" contract.
 *
 * <p>This gateway interposes a real {@link Propagation#REQUIRES_NEW} boundary
 * (a genuine cross-bean Spring proxy call, mirroring {@code JpaAuditService} and
 * the {@code BulkSubmitEvaluationsUseCase} pattern). Each profile row commits in
 * its OWN nested transaction: a failure rolls back only that row's writes and
 * leaves the batch transaction clean. ABAC, audit, the LOCKED guard and the
 * one-active-profile uniqueness check all still run inside the reused use-case
 * — nothing is bypassed.
 */
@Component
public class JobProfileImportGateway {

    private final CreateJobProfileUseCase createJobProfile;

    public JobProfileImportGateway(CreateJobProfileUseCase createJobProfile) {
        this.createJobProfile = createJobProfile;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JobProfile create(CreateJobProfileCommand cmd) {
        return createJobProfile.create(cmd);
    }
}
