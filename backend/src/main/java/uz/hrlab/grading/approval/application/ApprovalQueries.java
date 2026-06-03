package uz.hrlab.grading.approval.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequest;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.approval.domain.ApprovalStep;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestRepository;
import uz.hrlab.grading.approval.infrastructure.ApprovalStepJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalStepRepository;

import java.util.List;
import java.util.UUID;

@Service
public class ApprovalQueries {

    private final ApprovalRequestRepository requests;
    private final ApprovalStepRepository steps;

    public ApprovalQueries(ApprovalRequestRepository requests, ApprovalStepRepository steps) {
        this.requests = requests;
        this.steps = steps;
    }

    @Transactional(readOnly = true)
    public ApprovalRequest hydrate(ApprovalRequestJpaEntity req) {
        List<ApprovalStepJpaEntity> stepRows = steps
                .findAllByTenantIdAndApprovalRequestIdOrderByStepOrderAsc(
                        req.getTenantId(), req.getId());
        List<ApprovalStep> domainSteps = stepRows.stream().map(s -> new ApprovalStep(
                s.getId(), s.getApprovalRequestId(), s.getStepOrder(),
                s.getApproverUserId(), s.getRequiredPermission(),
                s.getStatus(), s.getDecidedBy(), s.getDecidedAt(), s.getReasonI18n()))
                .toList();
        return new ApprovalRequest(req.getId(), req.getTenantId(), req.getProjectId(),
                req.getEntityType(), req.getEntityId(), req.getRequestedBy(),
                req.getRequestedAt(), req.getCurrentStatus(), req.getNotesI18n(), domainSteps);
    }

    @Transactional(readOnly = true)
    public ApprovalStepJpaEntity findCurrentPendingStep(UUID tenantId, UUID approvalRequestId) {
        return steps.findFirstByTenantIdAndApprovalRequestIdAndStatusOrderByStepOrderAsc(
                tenantId, approvalRequestId,
                uz.hrlab.grading.approval.domain.ApprovalStepStatus.PENDING).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestJpaEntity> findActiveForEntity(UUID tenantId,
                                                              ApprovalEntityType entityType,
                                                              UUID entityId) {
        return requests.findAllByTenantIdAndEntityTypeAndEntityId(tenantId, entityType, entityId);
    }

    @Transactional(readOnly = true)
    public ApprovalRequestJpaEntity findActivePendingForEntity(UUID tenantId,
                                                               ApprovalEntityType entityType,
                                                               UUID entityId) {
        return requests.findFirstByTenantIdAndEntityTypeAndEntityIdAndCurrentStatus(
                tenantId, entityType, entityId, ApprovalRequestStatus.PENDING).orElse(null);
    }
}
