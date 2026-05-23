package uz.hrlab.grading.position.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.position.domain.Position;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

@Service
public class FindPositionQuery {

    private static final int MAX_PAGE_SIZE = 200;

    private final PositionRepository positions;
    private final AbacGate abacGate;

    public FindPositionQuery(PositionRepository positions, AbacGate abacGate) {
        this.positions = positions;
        this.abacGate = abacGate;
    }

    @Transactional(readOnly = true)
    public Position findById(UUID id) {
        TenantContext ctx = TenantContextHolder.requireActive();
        PositionJpaEntity entity = positions.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanReadPosition(ctx, entity.getId(), entity.getProjectId(),
                entity.getDepartmentId(), entity.getStatus());
        return entity.toDomain();
    }

    @Transactional(readOnly = true)
    public Page<Position> list(UUID projectId, UUID departmentId, PositionStatus status,
                               String jobFamily, int page, int size) {
        TenantContext ctx = TenantContextHolder.requireActive();
        // ABAC gate at listing scope: project membership + consultant assignment
        abacGate.enforceCanListInProject(ctx, projectId);

        // Viewer / Auditor → force ACTIVE-only at the query layer
        // (defense-in-depth on top of ApprovedEntityFilterPolicy).
        PositionStatus effectiveStatus = status;
        if (effectiveStatus == null
                && (ctx.hasRole(RoleCodes.VIEWER) || ctx.hasRole(RoleCodes.EXTERNAL_AUDITOR))
                && !ctx.hasRole(RoleCodes.HRLAB_SUPER_ADMIN)
                && !ctx.hasRole(RoleCodes.HRLAB_PROJECT_MANAGER)) {
            effectiveStatus = PositionStatus.ACTIVE;
        }

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<PositionJpaEntity> raw = positions.search(
                ctx.tenantId(), projectId, departmentId, effectiveStatus, jobFamily,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by("code").ascending()));
        return raw.map(PositionJpaEntity::toDomain);
    }
}
