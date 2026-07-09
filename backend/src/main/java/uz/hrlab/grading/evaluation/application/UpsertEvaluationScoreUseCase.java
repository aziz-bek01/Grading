package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.EvaluationImmutabilityPolicy;
import uz.hrlab.grading.evaluation.domain.EvaluationScore;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.FactorLevel;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotent upsert of one (evaluation, factor) → factorLevel selection.
 * Triggers a recompute of total + status afterwards.
 *
 * <p>Permission: EVALUATION_EDIT. Immutable on APPROVED / LOCKED / ARCHIVED.
 *
 * <p>E4-S3 SECURITY — scoring is gated by project membership AND the
 * department subtree of the evaluation's position. A department-scoped caller
 * ({@code DEPARTMENT_MANAGER} / {@code EVALUATION_COMMITTEE_MEMBER}) may only
 * score an evaluation whose position is INSIDE their assigned subtree; a
 * position outside it is rejected with a 404-equivalent
 * {@code TenantAccessDeniedException} (no existence reveal) and an
 * {@code ACCESS_DENIED_BY_ABAC} audit row. Tenant-wide / bypass roles are
 * unaffected.
 */
@Service
public class UpsertEvaluationScoreUseCase {

    private final EvaluationRepository evaluations;
    private final EvaluationScoreRepository scores;
    private final EvaluationContextLoader loader;
    private final EvaluationRecomputeService recompute;
    private final EvaluationImmutabilityPolicy immutability;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final EvaluationAuditSnapshot snapshot;
    private final PositionRepository positions;
    private final PanelCompletionWatcher panelCompletionWatcher;

    public UpsertEvaluationScoreUseCase(EvaluationRepository evaluations,
                                        EvaluationScoreRepository scores,
                                        EvaluationContextLoader loader,
                                        EvaluationRecomputeService recompute,
                                        EvaluationImmutabilityPolicy immutability,
                                        AbacGate abacGate,
                                        AuditService audit,
                                        EvaluationAuditSnapshot snapshot,
                                        PositionRepository positions,
                                        PanelCompletionWatcher panelCompletionWatcher) {
        this.evaluations = evaluations;
        this.scores = scores;
        this.loader = loader;
        this.recompute = recompute;
        this.immutability = immutability;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
        this.positions = positions;
        this.panelCompletionWatcher = panelCompletionWatcher;
    }

    @Transactional
    public EvaluationScore upsert(UpsertEvaluationScoreCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.EVALUATION_EDIT);
        if (cmd == null || cmd.evaluationId() == null || cmd.factorId() == null
                || cmd.factorLevelId() == null) {
            throw new ValidationException(
                    "evaluationId, factorId, and factorLevelId are required");
        }
        EvaluationContext context = loader.load(cmd.evaluationId(), ctx.tenantId());
        EvaluationJpaEntity evaluation = context.evaluation();
        immutability.enforceCanEdit(evaluation.getStatus());
        // E4-S3 — project membership + department-subtree write gate, resolved
        // via the evaluation's position. Out-of-subtree scoped callers denied.
        //
        // SELF-OWNERSHIP CARVE-OUT (mirrors EvaluationQueries.listMine): when the
        // caller IS this evaluation's own evaluator_user_id, the DEPARTMENT-SCOPE
        // write gate is short-circuited so a committee member with an EMPTY
        // department scope can score their OWN sheet (the P0 fix). This relaxes
        // ONLY the department-scope dimension and ONLY for the owner: the tenant
        // filter (already matched by loader.load) is untouched, every OTHER guard
        // above and below stays in force (EVALUATION_EDIT permission, status
        // immutability, methodology/factor-version validation, calibration reset),
        // and a NON-owner (manager) still requires the full subtree scope.
        boolean ownSheet = ctx.userId() != null
                && ctx.userId().equals(evaluation.getEvaluatorUserId());
        if (!ownSheet) {
            PositionJpaEntity position = positions
                    .findByIdAndTenantId(evaluation.getPositionId(), ctx.tenantId())
                    .orElseThrow(TenantAccessDeniedException::new);
            abacGate.enforceCanWriteInDepartment(
                    ctx, evaluation.getProjectId(), position.getDepartmentId());
        }

        Factor factor = findFactor(context.factors(), cmd.factorId());
        FactorLevel level = findLevel(context.levelsByFactor().get(factor.id()), cmd.factorLevelId());

        // Compute the raw factor score using the same engine that recompute will use.
        // We persist it now so the row carries the canonical value even before recompute.
        BigDecimal rawScore = recompute.preview(context,
                java.util.Map.of(factor.id(), level.id())).perFactorRaw().get(factor.id());

        Optional<EvaluationScoreJpaEntity> existing = scores
                .findByTenantIdAndEvaluationIdAndFactorId(
                        ctx.tenantId(), evaluation.getId(), factor.id());
        EvaluationScoreJpaEntity row;
        var beforeJson = existing.map(snapshot::of).orElse(null);
        if (existing.isPresent()) {
            row = existing.get();
            // A manually-adjusted row is reset to engine-computed on regular EDIT —
            // calibration must be re-applied explicitly via the calibrate use case.
            row.setFactorLevelId(level.id());
            row.setRawFactorScore(rawScore);
            row.setCommentText(cmd.comment());
            if (row.isManuallyAdjusted()) {
                row.setManuallyAdjusted(false);
                row.setOriginalFactorScore(null);
                row.setAdjustedByUserId(null);
                row.setAdjustedAt(null);
                row.setAdjustmentReason(null);
            }
        } else {
            row = new EvaluationScoreJpaEntity(
                    UUID.randomUUID(), ctx.tenantId(), evaluation.getId(),
                    factor.id(), level.id(), rawScore);
            row.setCommentText(cmd.comment());
        }
        scores.save(row);

        // Recompute totals + completeness; persists evaluation updates.
        recompute.recompute(context);
        evaluations.save(evaluation);

        // MVP2 multi-evaluator (BE-7) — if this evaluation belongs to a panel,
        // sync its assignment status and, when every active assignment is
        // COMPLETED, transition the panel to AVERAGED and compute the average.
        // No-op for panelless / legacy evaluations.
        panelCompletionWatcher.onEvaluationStatusRecomputed(evaluation, ctx.userId());

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(evaluation.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.EVALUATION_SCORE_UPSERTED)
                .entityType("EvaluationScore")
                .entityId(row.getId())
                .beforeJson(beforeJson)
                .afterJson(snapshot.of(row))
                .build());
        return row.toDomain();
    }

    /**
     * Defense-in-depth guard against the mixed-rows bug: {@code context.factors()}
     * holds ONLY the factors of the evaluation's own methodology version, so a
     * factor belonging to a DIFFERENT methodology version (e.g. an 8-factor
     * methodology's factor pushed at a 12-factor HR-Lab evaluation) is absent and
     * rejected with an explicit {@code FACTOR_VERSION_MISMATCH} code rather than a
     * generic 400. This mirrors the K-sheet read-side version scoping and ensures
     * a foreign-version score can never be persisted even if the read filter is
     * bypassed.
     */
    private static Factor findFactor(List<Factor> factors, UUID factorId) {
        return factors.stream()
                .filter(f -> factorId.equals(f.id()))
                .findFirst()
                .orElseThrow(() -> new ValidationException("FACTOR_VERSION_MISMATCH",
                        "Factor does not belong to this evaluation's methodology version"));
    }

    private static FactorLevel findLevel(List<FactorLevel> levels, UUID levelId) {
        if (levels == null) {
            throw new ValidationException("Factor has no levels");
        }
        return levels.stream()
                .filter(l -> levelId.equals(l.id()))
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        "FactorLevel does not belong to the given factor"));
    }
}
