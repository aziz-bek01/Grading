package uz.hrlab.grading.position.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

@Service
public class ArchivePositionUseCase {

    private final PositionRepository positions;
    private final AuditService audit;
    private final AbacGate abacGate;

    public ArchivePositionUseCase(PositionRepository positions,
                                  AuditService audit,
                                  AbacGate abacGate) {
        this.positions = positions;
        this.audit = audit;
        this.abacGate = abacGate;
    }

    @Transactional
    public void archive(UUID id) {
        TenantContext ctx = TenantContextHolder.requireActive();
        PositionJpaEntity entity = positions.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        // F-202 — ABAC project + department scope check on write.
        abacGate.enforceCanWriteInProject(ctx, entity.getProjectId());
        abacGate.enforceCanWriteInDepartment(ctx, entity.getProjectId(), entity.getDepartmentId());
        if (entity.getStatus() == PositionStatus.ARCHIVED) return;
        entity.setStatus(PositionStatus.ARCHIVED);
        positions.save(entity);
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(entity.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.POSITION_ARCHIVED)
                .entityType("Position")
                .entityId(id)
                .build());
    }
}
