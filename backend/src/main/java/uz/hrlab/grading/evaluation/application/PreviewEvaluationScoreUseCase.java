package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.EvaluationScoringEngine;
import uz.hrlab.grading.evaluation.domain.ScoringInputs;
import uz.hrlab.grading.evaluation.domain.ScoringResult;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.FactorLevel;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hypothetical scoring — does NOT modify state, NOT audited
 * (design-foundation §10 "Preview only" disclaimer; matches PRD Phase 5 AC).
 *
 * <p>Permission: EVALUATION_READ (anyone who can view scores can preview).
 */
@Service
public class PreviewEvaluationScoreUseCase {

    private final EvaluationContextLoader loader;
    private final EvaluationScoringEngine engine;

    public PreviewEvaluationScoreUseCase(EvaluationContextLoader loader,
                                         EvaluationScoringEngine engine) {
        this.loader = loader;
        this.engine = engine;
    }

    @Transactional(readOnly = true)
    public ScoringResult preview(PreviewScoreCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_READ)) {
            throw new PermissionDeniedException();
        }
        if (cmd == null || cmd.methodologyVersionId() == null) {
            throw new ValidationException("methodologyVersionId is required");
        }
        MethodologyVersion version = loader.loadVersion(cmd.methodologyVersionId(), ctx.tenantId());
        // BE-4 — preview is the "score a NEW evaluation" path: offer only ACTIVE
        // (non-deprecated) factors/levels. Existing-evaluation recompute uses the
        // unfiltered loader so historical deprecated rows still resolve.
        List<Factor> factors = loader.loadActiveFactors(version.id(), ctx.tenantId());
        Map<UUID, List<FactorLevel>> levelsByFactor = loader.loadActiveLevels(factors, ctx.tenantId());

        Map<UUID, UUID> selections = new LinkedHashMap<>();
        if (cmd.selections() != null) {
            for (PreviewScoreCommand.Selection s : cmd.selections()) {
                if (s != null && s.factorId() != null && s.factorLevelId() != null) {
                    selections.put(s.factorId(), s.factorLevelId());
                }
            }
        }
        return engine.compute(new ScoringInputs(version, factors, levelsByFactor, selections));
    }
}
