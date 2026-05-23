package uz.hrlab.grading.jobprofile.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.jobprofile.domain.JobProfile;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatus;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileJpaEntity;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.UUID;

/** Read-side queries for {@link JobProfile} (ABAC-checked). */
@Service
public class FindJobProfileQuery {

    private final JobProfileRepository profiles;
    private final PositionRepository positions;
    private final AbacGate abacGate;

    public FindJobProfileQuery(JobProfileRepository profiles,
                               PositionRepository positions,
                               AbacGate abacGate) {
        this.profiles = profiles;
        this.positions = positions;
        this.abacGate = abacGate;
    }

    @Transactional(readOnly = true)
    public JobProfile findById(UUID id) {
        TenantContext ctx = TenantContextHolder.requireActive();
        JobProfileJpaEntity entity = profiles.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        PositionJpaEntity position = positions
                .findByIdAndTenantId(entity.getPositionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanReadPosition(ctx, position.getId(), position.getProjectId(),
                position.getDepartmentId(), entity.getStatus());
        return entity.toDomain();
    }

    @Transactional(readOnly = true)
    public JobProfile findActiveByPositionId(UUID positionId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        PositionJpaEntity position = positions
                .findByIdAndTenantId(positionId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanReadPosition(ctx, position.getId(), position.getProjectId(),
                position.getDepartmentId(), null);
        JobProfileJpaEntity entity = profiles
                .findFirstByTenantIdAndProjectIdAndPositionIdAndStatusNot(
                        ctx.tenantId(), position.getProjectId(), position.getId(),
                        JobProfileStatus.ARCHIVED)
                .orElseThrow(TenantAccessDeniedException::new);
        return entity.toDomain();
    }

    @Transactional(readOnly = true)
    public List<JobProfile> listRevisionsByPositionId(UUID positionId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        PositionJpaEntity position = positions
                .findByIdAndTenantId(positionId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanReadPosition(ctx, position.getId(), position.getProjectId(),
                position.getDepartmentId(), null);
        return profiles
                .findAllByTenantIdAndProjectIdAndPositionIdOrderByRevisionNumberDesc(
                        ctx.tenantId(), position.getProjectId(), position.getId())
                .stream()
                .map(JobProfileJpaEntity::toDomain)
                .toList();
    }
}
