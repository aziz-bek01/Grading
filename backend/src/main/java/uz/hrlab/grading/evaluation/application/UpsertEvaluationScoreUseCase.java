package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
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

    public UpsertEvaluationScoreUseCase(EvaluationRepository evaluations,
                                        EvaluationScoreRepository scores,
                                        EvaluationContextLoader loader,
                                        EvaluationRecomputeService recompute,
                                        EvaluationImmutabilityPolicy immutability,
                                        AbacGate abacGate,
                                        AuditService audit,
                                        EvaluationAuditSnapshot snapshot) {
        this.evaluations = evaluations;
        this.scores = scores;
        this.loader = loader;
        this.recompute = recompute;
        this.immutability = immutability;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public EvaluationScore upsert(UpsertEvaluationScoreCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_EDIT)) {
            throw new PermissionDeniedException();
        }
        if (cmd == null || cmd.evaluationId() == null || cmd.factorId() == null
                || cmd.factorLevelId() == null) {
            throw new ValidationException(
                    "evaluationId, factorId, and factorLevelId are required");
        }
        EvaluationContext context = loader.load(cmd.evaluationId(), ctx.tenantId());
        EvaluationJpaEntity evaluation = context.evaluation();
        immutability.enforceCanEdit(evaluation.getStatus());
        abacGate.enforceCanWriteInProject(ctx, evaluation.getProjectId());

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

    private static Factor findFactor(List<Factor> factors, UUID factorId) {
        return factors.stream()
                .filter(f -> factorId.equals(f.id()))
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        "Factor not found in this evaluation's methodology version"));
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
