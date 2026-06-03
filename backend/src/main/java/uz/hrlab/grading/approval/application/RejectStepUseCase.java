package uz.hrlab.grading.approval.application;

import org.springframework.stereotype.Service;
import uz.hrlab.grading.approval.domain.ApprovalRequest;

import java.util.UUID;

@Service
public class RejectStepUseCase {

    private final ApprovalDecisionMaker decisionMaker;

    public RejectStepUseCase(ApprovalDecisionMaker decisionMaker) {
        this.decisionMaker = decisionMaker;
    }

    public ApprovalRequest reject(UUID requestId, UUID stepId, String reason) {
        return decisionMaker.reject(requestId, stepId, reason);
    }
}
