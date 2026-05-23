package uz.hrlab.grading.jobprofile.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.jobprofile.application.CreateJobProfileCommand;
import uz.hrlab.grading.jobprofile.application.CreateJobProfileUseCase;
import uz.hrlab.grading.jobprofile.application.FindJobProfileQuery;
import uz.hrlab.grading.jobprofile.domain.JobProfile;

import java.util.List;
import java.util.UUID;

/**
 * Position-scoped job profile endpoints (PRD §E6 + architecture §13.1).
 * {@code positionId} comes from the URL path; tenantId is server-derived.
 */
@RestController
@RequestMapping("/api/v1/positions/{positionId}/job-profile")
public class PositionJobProfileController {

    private final CreateJobProfileUseCase createUseCase;
    private final FindJobProfileQuery findQuery;

    public PositionJobProfileController(CreateJobProfileUseCase createUseCase,
                                        FindJobProfileQuery findQuery) {
        this.createUseCase = createUseCase;
        this.findQuery = findQuery;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('JOB_PROFILE_EDIT')")
    public ResponseEntity<JobProfileResponse> create(@PathVariable UUID positionId,
                                                     @Valid @RequestBody CreateJobProfileRequest req) {
        JobProfile created = createUseCase.create(new CreateJobProfileCommand(
                positionId,
                req.purposeI18n(), req.mainDutiesI18n(), req.responsibilityAreaI18n(),
                req.authorityI18n(), req.kpiExpectedResultsI18n(),
                req.educationRequirementsI18n(), req.experienceRequirementsI18n(),
                req.knowledgeSkillsI18n(), req.internalInteractionsI18n(),
                req.externalInteractionsI18n(), req.workingConditionsI18n(),
                req.documentsRegulationsI18n(), req.actualizationDate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(JobProfileResponse.from(created));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('JOB_PROFILE_READ')")
    public JobProfileResponse getActive(@PathVariable UUID positionId) {
        return JobProfileResponse.from(findQuery.findActiveByPositionId(positionId));
    }

    @GetMapping("/revisions")
    @PreAuthorize("hasAuthority('JOB_PROFILE_READ')")
    public List<JobProfileResponse> listRevisions(@PathVariable UUID positionId) {
        return findQuery.listRevisionsByPositionId(positionId).stream()
                .map(JobProfileResponse::from)
                .toList();
    }
}
