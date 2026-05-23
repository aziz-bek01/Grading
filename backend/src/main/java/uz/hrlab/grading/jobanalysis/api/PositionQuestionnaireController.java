package uz.hrlab.grading.jobanalysis.api;

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
import uz.hrlab.grading.jobanalysis.application.CreateQuestionnaireCommand;
import uz.hrlab.grading.jobanalysis.application.CreateQuestionnaireUseCase;
import uz.hrlab.grading.jobanalysis.application.FindQuestionnaireQuery;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/positions/{positionId}/questionnaires")
public class PositionQuestionnaireController {

    private final CreateQuestionnaireUseCase createUseCase;
    private final FindQuestionnaireQuery findQuery;

    public PositionQuestionnaireController(CreateQuestionnaireUseCase createUseCase,
                                           FindQuestionnaireQuery findQuery) {
        this.createUseCase = createUseCase;
        this.findQuery = findQuery;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('JOB_ANALYSIS_EDIT')")
    public ResponseEntity<QuestionnaireResponse> create(
            @PathVariable UUID positionId,
            @Valid @RequestBody CreateQuestionnaireRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                QuestionnaireResponse.from(createUseCase.create(
                        new CreateQuestionnaireCommand(positionId, req.templateCode()))));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('JOB_ANALYSIS_READ')")
    public List<QuestionnaireResponse> list(@PathVariable UUID positionId) {
        return findQuery.listByPositionId(positionId).stream()
                .map(QuestionnaireResponse::from).toList();
    }
}
