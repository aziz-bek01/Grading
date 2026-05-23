package uz.hrlab.grading.jobprofile.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.jobprofile.application.ApproveJobProfileUseCase;
import uz.hrlab.grading.jobprofile.application.ArchiveJobProfileUseCase;
import uz.hrlab.grading.jobprofile.application.CreateJobProfileRevisionUseCase;
import uz.hrlab.grading.jobprofile.application.FindJobProfileQuery;
import uz.hrlab.grading.jobprofile.application.RequestJobProfileChangesUseCase;
import uz.hrlab.grading.jobprofile.application.SubmitJobProfileForReviewUseCase;
import uz.hrlab.grading.jobprofile.application.UpdateJobProfileCommand;
import uz.hrlab.grading.jobprofile.application.UpdateJobProfileUseCase;

import java.util.UUID;

/**
 * Profile-id-scoped endpoints (PRD §E6). All status transitions are
 * gated by the workflow state machine in the use cases — controllers only
 * resolve permissions + path variables.
 */
@RestController
@RequestMapping("/api/v1/job-profiles")
public class JobProfileController {

    private final FindJobProfileQuery findQuery;
    private final UpdateJobProfileUseCase updateUseCase;
    private final SubmitJobProfileForReviewUseCase submitUseCase;
    private final ApproveJobProfileUseCase approveUseCase;
    private final RequestJobProfileChangesUseCase requestChangesUseCase;
    private final ArchiveJobProfileUseCase archiveUseCase;
    private final CreateJobProfileRevisionUseCase revisionUseCase;

    public JobProfileController(FindJobProfileQuery findQuery,
                                UpdateJobProfileUseCase updateUseCase,
                                SubmitJobProfileForReviewUseCase submitUseCase,
                                ApproveJobProfileUseCase approveUseCase,
                                RequestJobProfileChangesUseCase requestChangesUseCase,
                                ArchiveJobProfileUseCase archiveUseCase,
                                CreateJobProfileRevisionUseCase revisionUseCase) {
        this.findQuery = findQuery;
        this.updateUseCase = updateUseCase;
        this.submitUseCase = submitUseCase;
        this.approveUseCase = approveUseCase;
        this.requestChangesUseCase = requestChangesUseCase;
        this.archiveUseCase = archiveUseCase;
        this.revisionUseCase = revisionUseCase;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('JOB_PROFILE_READ')")
    public JobProfileResponse getById(@PathVariable UUID id) {
        return JobProfileResponse.from(findQuery.findById(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('JOB_PROFILE_EDIT')")
    public JobProfileResponse update(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateJobProfileRequest req) {
        return JobProfileResponse.from(updateUseCase.update(id, new UpdateJobProfileCommand(
                req.purposeI18n(), req.mainDutiesI18n(), req.responsibilityAreaI18n(),
                req.authorityI18n(), req.kpiExpectedResultsI18n(),
                req.educationRequirementsI18n(), req.experienceRequirementsI18n(),
                req.knowledgeSkillsI18n(), req.internalInteractionsI18n(),
                req.externalInteractionsI18n(), req.workingConditionsI18n(),
                req.documentsRegulationsI18n(), req.actualizationDate())));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('JOB_PROFILE_EDIT')")
    public JobProfileResponse submit(@PathVariable UUID id) {
        return JobProfileResponse.from(submitUseCase.submit(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('JOB_PROFILE_APPROVE')")
    public JobProfileResponse approve(@PathVariable UUID id) {
        return JobProfileResponse.from(approveUseCase.approve(id));
    }

    @PostMapping("/{id}/request-changes")
    @PreAuthorize("hasAuthority('JOB_PROFILE_APPROVE')")
    public JobProfileResponse requestChanges(@PathVariable UUID id,
                                             @Valid @RequestBody ReasonRequest req) {
        return JobProfileResponse.from(requestChangesUseCase.requestChanges(id, req.reason()));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('JOB_PROFILE_EDIT')")
    public JobProfileResponse archive(@PathVariable UUID id,
                                      @Valid @RequestBody ReasonRequest req) {
        return JobProfileResponse.from(archiveUseCase.archive(id, req.reason()));
    }

    @PostMapping("/{id}/create-revision")
    @PreAuthorize("hasAuthority('JOB_PROFILE_EDIT')")
    public ResponseEntity<JobProfileResponse> createRevision(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(JobProfileResponse.from(revisionUseCase.createRevision(id)));
    }
}
