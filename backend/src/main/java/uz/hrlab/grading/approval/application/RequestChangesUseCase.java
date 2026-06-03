package uz.hrlab.grading.approval.application;

import org.springframework.stereotype.Service;
import uz.hrlab.grading.approval.domain.ApprovalRequest;

import java.util.UUID;

@Service
public class RequestChangesUseCase {

    private final ApprovalDecisionMaker decisionMaker;

    public RequestChangesUseCase(ApprovalDecisionMaker decisionMaker) {
        this.decisionMaker = decisionMaker;
    }

    public ApprovalRequest requestChanges(UUID requestId, UUID stepId, String reason) {
        return decisionMaker.requestChanges(requestId, stepId, reason);
    }
}
