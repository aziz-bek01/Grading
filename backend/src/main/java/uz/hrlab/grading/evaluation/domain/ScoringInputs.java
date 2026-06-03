package uz.hrlab.grading.evaluation.domain;

import uz.hrlab.grading.methodology.domain.Factor;
import uz.hrlab.grading.methodology.domain.FactorLevel;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure input bundle for {@link EvaluationScoringEngine}. All collections are
 * read-only.
 *
 * @param version         the methodology version (carries {@code scoringMode})
 * @param factors         all factors of the version
 * @param levelsByFactor  factor id → ordered factor levels
 * @param selections      factor id → selected factor-level id
 */
public record ScoringInputs(
        MethodologyVersion version,
        List<Factor> factors,
        Map<UUID, List<FactorLevel>> levelsByFactor,
        Map<UUID, UUID> selections
) {
}
