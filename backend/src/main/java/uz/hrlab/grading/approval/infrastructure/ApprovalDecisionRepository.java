package uz.hrlab.grading.approval.infrastructure;

import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.List;
import java.util.UUID;

public interface ApprovalDecisionRepository
        extends TenantAwareRepository<ApprovalDecisionJpaEntity, UUID> {

    List<ApprovalDecisionJpaEntity>
            findAllByTenantIdAndApprovalStepIdOrderByDecidedAtAsc(
                    UUID tenantId, UUID approvalStepId);
}
