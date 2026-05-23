package uz.hrlab.grading.jobanalysis.domain;

import uz.hrlab.grading.common.exception.BaseDomainException;

public class QuestionnaireTransitionRejectedException extends BaseDomainException {

    public QuestionnaireTransitionRejectedException(String safeMessage) {
        super("QUESTIONNAIRE_TRANSITION_REJECTED", safeMessage);
    }

    public QuestionnaireTransitionRejectedException(String code, String safeMessage) {
        super(code, safeMessage);
    }
}
