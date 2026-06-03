package uz.hrlab.grading.evaluation.domain;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.methodology.domain.Factor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Domain service: determines whether an evaluation has every required factor
 * scored (architecture §15.5).
 *
 * <p>Returns the list of missing required factor ids — empty list means all
 * required factors are scored (status can be COMPLETE).
 */
@Component
public class EvaluationCompletenessChecker {

    /** @return ids of required factors that are not present in {@code scoredFactorIds}. */
    public List<UUID> missingRequiredFactors(List<Factor> factors, Set<UUID> scoredFactorIds) {
        if (factors == null) return List.of();
        List<UUID> missing = new ArrayList<>();
        for (Factor f : factors) {
            if (!f.required()) continue;
            if (scoredFactorIds == null || !scoredFactorIds.contains(f.id())) {
                missing.add(f.id());
            }
        }
        return missing;
    }

    /**
     * Computes the next non-terminal status based on completeness.
     * Only operates on DRAFT/INCOMPLETE/COMPLETE — other statuses are returned
     * unchanged (immutable / committed).
     */
    public EvaluationStatus recomputeStatus(EvaluationStatus current,
                                            List<Factor> factors,
                                            Set<UUID> scoredFactorIds) {
        if (current != EvaluationStatus.DRAFT
                && current != EvaluationStatus.INCOMPLETE
                && current != EvaluationStatus.COMPLETE) {
            return current;
        }
        // No scores at all → DRAFT.
        if (scoredFactorIds == null || scoredFactorIds.isEmpty()) {
            return EvaluationStatus.DRAFT;
        }
        return missingRequiredFactors(factors, scoredFactorIds).isEmpty()
                ? EvaluationStatus.COMPLETE
                : EvaluationStatus.INCOMPLETE;
    }

    /** Convenience for use cases: factors → factor id set retained from scores. */
    public boolean hasAllRequired(List<Factor> factors, Map<UUID, ?> scoresByFactorId) {
        return missingRequiredFactors(factors,
                scoresByFactorId == null ? Set.of() : scoresByFactorId.keySet()).isEmpty();
    }
}
