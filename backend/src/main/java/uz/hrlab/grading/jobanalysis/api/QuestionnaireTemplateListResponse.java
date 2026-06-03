package uz.hrlab.grading.jobanalysis.api;

import java.util.List;

/** Wrapper so the catalogue is returned as an object with an {@code items} array. */
public record QuestionnaireTemplateListResponse(
        List<QuestionnaireTemplateResponse> items
) {
}
