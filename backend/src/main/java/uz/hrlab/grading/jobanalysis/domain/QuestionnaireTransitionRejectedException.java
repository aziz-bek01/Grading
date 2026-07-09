package uz.hrlab.grading.jobanalysis.domain;

import uz.hrlab.grading.common.exception.DomainTransitionRejectedException;

public class QuestionnaireTransitionRejectedException extends DomainTransitionRejectedException {

    public QuestionnaireTransitionRejectedException(String safeMessage) {
        super("QUESTIONNAIRE_TRANSITION_REJECTED", safeMessage);
    }

    public QuestionnaireTransitionRejectedException(String code, String safeMessage) {
        super(code, safeMessage);
    }
}
