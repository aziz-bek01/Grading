package uz.hrlab.grading.evaluation.application;

import java.util.List;
import java.util.UUID;

/**
 * Hypothetical score request. No persistence side-effects.
 *
 * @param methodologyVersionId  the methodology to score against
 * @param selections            list of (factorId, factorLevelId) pairs
 */
public record PreviewScoreCommand(
        UUID methodologyVersionId,
        List<Selection> selections
) {
    public record Selection(UUID factorId, UUID factorLevelId) { }
}
