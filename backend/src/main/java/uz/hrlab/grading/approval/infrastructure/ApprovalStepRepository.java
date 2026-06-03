package uz.hrlab.grading.approval.infrastructure;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.approval.domain.ApprovalStepStatus;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalStepRepository
        extends TenantAwareRepository<ApprovalStepJpaEntity, UUID> {

    List<ApprovalStepJpaEntity>
            findAllByTenantIdAndApprovalRequestIdOrderByStepOrderAsc(
                    UUID tenantId, UUID approvalRequestId);

    Optional<ApprovalStepJpaEntity>
            findFirstByTenantIdAndApprovalRequestIdAndStatusOrderByStepOrderAsc(
                    UUID tenantId, UUID approvalRequestId, ApprovalStepStatus status);

    /**
     * Inbox query — steps that are PENDING and either explicitly assigned to
     * the user OR have approverUserId NULL (anyone with the required
     * permission can act).
     */
    @Query("""
            SELECT s FROM ApprovalStepJpaEntity s
             WHERE s.tenantId = :tenantId
               AND s.status = uz.hrlab.grading.approval.domain.ApprovalStepStatus.PENDING
               AND (s.approverUserId = :userId
                    OR (s.approverUserId IS NULL
                        AND s.requiredPermission IN :userPermissions))
            """)
    List<ApprovalStepJpaEntity> findInbox(@Param("tenantId") UUID tenantId,
                                          @Param("userId") UUID userId,
                                          @Param("userPermissions") List<String> userPermissions);
}
