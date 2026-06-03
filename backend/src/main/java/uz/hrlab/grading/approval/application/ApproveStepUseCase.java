package uz.hrlab.grading.approval.application;

import org.springframework.stereotype.Service;
import uz.hrlab.grading.approval.domain.ApprovalRequest;

import java.util.UUID;

@Service
public class ApproveStepUseCase {

    private final ApprovalDecisionMaker decisionMaker;

    public ApproveStepUseCase(ApprovalDecisionMaker decisionMaker) {
        this.decisionMaker = decisionMaker;
    }

    public ApprovalRequest approve(UUID requestId, UUID stepId, String notes) {
        return decisionMaker.approve(requestId, stepId, notes);
    }
}
