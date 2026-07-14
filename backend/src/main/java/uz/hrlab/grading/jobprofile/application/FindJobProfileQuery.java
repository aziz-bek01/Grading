package uz.hrlab.grading.jobprofile.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.common.api.Pagination;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.jobprofile.domain.JobProfile;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatus;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileJpaEntity;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    /**
     * PAGE-2 BE — bulk "active job-profile status by position ids" for ONE page
     * of the Positions Catalog. Replaces the client's per-position fan-out
     * (a page of {@code GET /positions/{id}/job-profile} calls) with TWO
     * tenant-scoped batch queries — one position load + one job-profile load —
     * and NO per-id DB round-trip.
     *
     * <p>AUTHORIZATION — identical semantics to {@link #findActiveByPositionId}:
     * every candidate position is run through the SAME
     * {@link AbacGate#enforceCanReadPosition} the per-position endpoint uses
     * (project membership + department subtree + …). A position the caller may
     * NOT read is simply OMITTED (treated exactly like "no active profile" — the
     * client renders an absent id as "no profile yet"); it is never leaked and a
     * single out-of-scope id never aborts the whole page. Positions with no
     * active (non-ARCHIVED) profile are likewise absent. The ABAC filter loops
     * only in-memory policy checks — the DB is touched exactly twice.
     *
     * <p>Input is bounded to {@link Pagination#MAX_PAGE_SIZE} ids and rejected
     * when empty ({@code 400 VALIDATION_FAILED}) so an unbounded list cannot fan
     * out server-side.
     */
    @Transactional(readOnly = true)
    public List<JobProfile> findActiveStatusesByPositionIds(Collection<UUID> positionIds) {
        if (positionIds == null || positionIds.isEmpty()) {
            throw new ValidationException("POSITION_IDS_REQUIRED",
                    "positionIds must not be empty");
        }
        Set<UUID> distinctIds = new LinkedHashSet<>(positionIds);
        if (distinctIds.size() > Pagination.MAX_PAGE_SIZE) {
            throw new ValidationException("POSITION_IDS_LIMIT_EXCEEDED",
                    "positionIds is capped at " + Pagination.MAX_PAGE_SIZE + " per call");
        }

        TenantContext ctx = TenantContextHolder.requireActive();

        // (1) ONE tenant-scoped position load — cross-tenant / unknown ids drop
        //     out (findAllByTenantIdAndIdIn pins tenant_id; never a bare findAllById).
        List<PositionJpaEntity> found =
                positions.findAllByTenantIdAndIdIn(ctx.tenantId(), distinctIds);

        // Per-position ABAC filter reusing the SAME gate as the per-position read.
        // A denial omits the position (never a leak, never a page-wide abort).
        Set<UUID> readableIds = new LinkedHashSet<>();
        for (PositionJpaEntity p : found) {
            try {
                abacGate.enforceCanReadPosition(ctx, p.getId(), p.getProjectId(),
                        p.getDepartmentId(), null);
                readableIds.add(p.getId());
            } catch (TenantAccessDeniedException denied) {
                // Out-of-scope position → omit (indistinguishable from "no profile").
            }
        }
        if (readableIds.isEmpty()) {
            return List.of();
        }

        // (2) ONE tenant-scoped batch load of the active (non-ARCHIVED) profile per
        //     readable position (partial-unique index ⇒ ≤ 1 row per position).
        return profiles
                .findAllByTenantIdAndPositionIdInAndStatusNot(
                        ctx.tenantId(), readableIds, JobProfileStatus.ARCHIVED)
                .stream()
                .map(JobProfileJpaEntity::toDomain)
                .toList();
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
