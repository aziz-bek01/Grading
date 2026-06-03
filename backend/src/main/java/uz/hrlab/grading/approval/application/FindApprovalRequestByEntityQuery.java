package uz.hrlab.grading.approval.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequest;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestJpaEntity;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.UUID;

@Service
public class FindApprovalRequestByEntityQuery {

    private final ApprovalQueries queries;

    public FindApprovalRequestByEntityQuery(ApprovalQueries queries) {
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> findAll(ApprovalEntityType entityType, UUID entityId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        List<ApprovalRequestJpaEntity> rows = queries
                .findActiveForEntity(ctx.tenantId(), entityType, entityId);
        return rows.stream().map(queries::hydrate).toList();
    }
}
