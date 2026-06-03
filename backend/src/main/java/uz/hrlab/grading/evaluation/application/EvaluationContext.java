package uz.hrlab.grading.evaluation.application;

import uz.hrlab.grading.evaluation.domain.ScoringInputs;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.FactorLevel;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;

import java.util.List;
import java.util.Map;

/**
 * Pre-loaded bundle used by use cases — keeps the load-once pattern explicit.
 *
 * @param evaluation       the JPA entity (mutable for status / score updates)
 * @param version          methodology version domain model
 * @param factors          all factors of the version (ordered)
 * @param levelsByFactor   factor id → levels (ordered)
 */
public record EvaluationContext(
        EvaluationJpaEntity evaluation,
        MethodologyVersion version,
        List<Factor> factors,
        Map<java.util.UUID, List<FactorLevel>> levelsByFactor
) {
    public ScoringInputs toScoringInputs(Map<java.util.UUID, java.util.UUID> selections) {
        return new ScoringInputs(version, factors, levelsByFactor, selections);
    }
}
