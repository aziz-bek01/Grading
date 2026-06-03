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
import java.util.Optional;
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

    /**
     * Active (non-archived) profile for a position, or empty when none exists yet.
     *
     * <p>"No profile yet" is a NORMAL state (the UI then offers to create one) and
     * MUST be distinguished from a real ABAC denial. Position-not-found and the
     * {@code abacGate.enforce} call still throw {@link TenantAccessDeniedException}
     * (genuine cross-tenant / position-access failures); only the missing-profile
     * case returns {@link Optional#empty()}.
     */
    @Transactional(readOnly = true)
    public Optional<JobProfile> findActiveByPositionId(UUID positionId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        PositionJpaEntity position = positions
                .findByIdAndTenantId(positionId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        abacGate.enforceCanReadPosition(ctx, position.getId(), position.getProjectId(),
                position.getDepartmentId(), null);
        return profiles
                .findFirstByTenantIdAndProjectIdAndPositionIdAndStatusNot(
                        ctx.tenantId(), position.getProjectId(), position.getId(),
                        JobProfileStatus.ARCHIVED)
                .map(JobProfileJpaEntity::toDomain);
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
