package uz.hrlab.grading.approval.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.common.infrastructure.TenantAwareRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRequestRepository
        extends TenantAwareRepository<ApprovalRequestJpaEntity, UUID> {

    Page<ApprovalRequestJpaEntity> findAllByTenantIdAndProjectId(
            UUID tenantId, UUID projectId, Pageable pageable);

    List<ApprovalRequestJpaEntity> findAllByTenantIdAndEntityTypeAndEntityId(
            UUID tenantId, ApprovalEntityType entityType, UUID entityId);

    Optional<ApprovalRequestJpaEntity>
            findFirstByTenantIdAndEntityTypeAndEntityIdAndCurrentStatus(
                    UUID tenantId, ApprovalEntityType entityType, UUID entityId,
                    ApprovalRequestStatus currentStatus);

    @Query("""
            SELECT a FROM ApprovalRequestJpaEntity a
             WHERE a.tenantId = :tenantId
               AND (:projectId IS NULL OR a.projectId = :projectId)
               AND (:entityType IS NULL OR a.entityType = :entityType)
               AND (:status IS NULL OR a.currentStatus = :status)
            """)
    Page<ApprovalRequestJpaEntity> search(
            @Param("tenantId") UUID tenantId,
            @Param("projectId") UUID projectId,
            @Param("entityType") ApprovalEntityType entityType,
            @Param("status") ApprovalRequestStatus status,
            Pageable pageable);
}
