package uz.hrlab.grading.jobanalysis.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.jobanalysis.application.QuestionnaireTemplate;

import java.util.List;
import java.util.Map;

/**
 * Read-only catalogue of the job-analysis questionnaire templates the frontend
 * lets a user pick from when instantiating a questionnaire for a position.
 *
 * <p>The catalogue is the static code-resident {@link QuestionnaireTemplate}
 * (MVP 1) — no database read, no tenant scoping, no audit event (it is the same
 * fixed list for every tenant). Gated with {@code JOB_ANALYSIS_READ}, the same
 * authority {@link QuestionnaireController} uses for questionnaire reads.
 */
@RestController
@RequestMapping("/api/v1/questionnaire-templates")
public class QuestionnaireTemplateController {

    private static final List<String> TEMPLATE_CODES = List.of(
            QuestionnaireTemplate.STANDARD_V1,
            QuestionnaireTemplate.EXECUTIVE_V1);

    @GetMapping
    @PreAuthorize("hasAuthority('JOB_ANALYSIS_READ')")
    public QuestionnaireTemplateListResponse list() {
        List<QuestionnaireTemplateResponse> items = TEMPLATE_CODES.stream()
                .map(QuestionnaireTemplateController::toResponse)
                .toList();
        return new QuestionnaireTemplateListResponse(items);
    }

    private static QuestionnaireTemplateResponse toResponse(String code) {
        Map<String, String> nameI18n = QuestionnaireTemplate.nameI18nFor(code);
        int questionCount = QuestionnaireTemplate.questionsFor(code).size();
        return new QuestionnaireTemplateResponse(code, nameI18n, questionCount);
    }
}
