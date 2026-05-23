package uz.hrlab.grading.jobanalysis.api;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.jobanalysis.application.ArchiveQuestionnaireUseCase;
import uz.hrlab.grading.jobanalysis.application.FindQuestionnaireQuery;
import uz.hrlab.grading.jobanalysis.application.SubmitQuestionnaireUseCase;
import uz.hrlab.grading.jobanalysis.application.UpdateAnswerCommand;
import uz.hrlab.grading.jobanalysis.application.UpdateAnswerUseCase;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/questionnaires")
public class QuestionnaireController {

    private final FindQuestionnaireQuery findQuery;
    private final UpdateAnswerUseCase updateAnswerUseCase;
    private final SubmitQuestionnaireUseCase submitUseCase;
    private final ArchiveQuestionnaireUseCase archiveUseCase;

    public QuestionnaireController(FindQuestionnaireQuery findQuery,
                                   UpdateAnswerUseCase updateAnswerUseCase,
                                   SubmitQuestionnaireUseCase submitUseCase,
                                   ArchiveQuestionnaireUseCase archiveUseCase) {
        this.findQuery = findQuery;
        this.updateAnswerUseCase = updateAnswerUseCase;
        this.submitUseCase = submitUseCase;
        this.archiveUseCase = archiveUseCase;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('JOB_ANALYSIS_READ')")
    public QuestionnaireResponse getById(@PathVariable UUID id) {
        return QuestionnaireResponse.from(findQuery.findById(id));
    }

    @GetMapping("/{id}/answers")
    @PreAuthorize("hasAuthority('JOB_ANALYSIS_READ')")
    public List<AnswerResponse> listAnswers(@PathVariable UUID id) {
        return findQuery.listAnswers(id).stream().map(AnswerResponse::from).toList();
    }

    @PatchMapping("/{id}/answers/{questionId}")
    @PreAuthorize("hasAuthority('JOB_ANALYSIS_EDIT')")
    public AnswerResponse updateAnswer(@PathVariable UUID id,
                                       @PathVariable UUID questionId,
                                       @Valid @RequestBody UpdateAnswerRequest req) {
        return AnswerResponse.from(updateAnswerUseCase.upsert(id, questionId,
                new UpdateAnswerCommand(req.answerText(), req.answerChoices(),
                        req.answerNumber())));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('JOB_ANALYSIS_EDIT')")
    public QuestionnaireResponse submit(@PathVariable UUID id) {
        return QuestionnaireResponse.from(submitUseCase.submit(id));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('JOB_ANALYSIS_EDIT')")
    public QuestionnaireResponse archive(@PathVariable UUID id,
                                         @Valid @RequestBody ArchiveQuestionnaireRequest req) {
        return QuestionnaireResponse.from(archiveUseCase.archive(id, req.reason()));
    }
}
