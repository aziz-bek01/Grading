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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
     *
     * <p>M-1 — after loading the active profile, the profile's REAL status is fed
     * back through {@link AbacGate#enforceCanReadPosition} so
     * {@code ApprovedEntityFilterPolicy} withholds a DRAFT/UNDER_REVIEW profile
     * from a Viewer/External-Auditor, exactly as {@code GET /job-profiles/{id}}
     * already does. A withheld draft is returned as {@link Optional#empty()}
     * (absent → 204) — indistinguishable from "no profile yet" — rather than
     * surfaced. Consultant/HR roles are {@code NOT_APPLICABLE} to that filter and
     * still see drafts.
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
                .filter(profile -> canReadProfileStatus(ctx, position, profile.getStatus()))
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
        // Keyed by position id so the profile-status re-check below (M-1) can
        // resolve each returned profile's owning position.
        Map<UUID, PositionJpaEntity> readablePositions = new LinkedHashMap<>();
        for (PositionJpaEntity p : found) {
            try {
                abacGate.enforceCanReadPosition(ctx, p.getId(), p.getProjectId(),
                        p.getDepartmentId(), null);
                readablePositions.put(p.getId(), p);
            } catch (TenantAccessDeniedException denied) {
                // Out-of-scope position → omit (indistinguishable from "no profile").
            }
        }
        if (readablePositions.isEmpty()) {
            return List.of();
        }

        // (2) ONE tenant-scoped batch load of the active (non-ARCHIVED) profile per
        //     readable position (partial-unique index ⇒ ≤ 1 row per position).
        //     L-1: no projectId predicate here (unlike the singular finder) — the
        //     positionId IN-list is the readable, per-position ABAC-vetted set,
        //     tenant_id is pinned, and positionId→projectId is 1:1, so a projectId
        //     predicate would be tautological (can exclude no row the id-set doesn't).
        List<JobProfileJpaEntity> active = profiles
                .findAllByTenantIdAndPositionIdInAndStatusNot(
                        ctx.tenantId(), readablePositions.keySet(), JobProfileStatus.ARCHIVED);

        // M-1: apply the approved-status filter with each profile's REAL status so
        // a Viewer/External-Auditor never sees a DRAFT/UNDER_REVIEW profile (parity
        // with GET /job-profiles/{id}); a withheld profile is simply omitted.
        List<JobProfile> result = new ArrayList<>(active.size());
        for (JobProfileJpaEntity profile : active) {
            PositionJpaEntity position = readablePositions.get(profile.getPositionId());
            if (position != null && canReadProfileStatus(ctx, position, profile.getStatus())) {
                result.add(profile.toDomain());
            }
        }
        return result;
    }

    /**
     * M-1 — re-runs the position read gate with a profile's REAL status so the
     * {@link uz.hrlab.grading.access.domain.ApprovedEntityFilterPolicy} withholds a
     * non-approved (DRAFT/UNDER_REVIEW) profile from a Viewer/External-Auditor,
     * consistent with the {@code GET /job-profiles/{id}} path. Returns {@code true}
     * when the caller may see a profile in that status; a policy denial maps to
     * {@code false} (the profile is omitted/absent, never surfaced). Roles that are
     * {@code NOT_APPLICABLE} to the filter (Consultant/HR/…) always see the profile.
     * The position-level policies were already satisfied by the {@code status=null}
     * call above, so the only new source of denial here is the status filter.
     */
    private boolean canReadProfileStatus(TenantContext ctx, PositionJpaEntity position,
                                         JobProfileStatus status) {
        try {
            abacGate.enforceCanReadPosition(ctx, position.getId(), position.getProjectId(),
                    position.getDepartmentId(), status);
            return true;
        } catch (TenantAccessDeniedException withheld) {
            return false;
        }
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
