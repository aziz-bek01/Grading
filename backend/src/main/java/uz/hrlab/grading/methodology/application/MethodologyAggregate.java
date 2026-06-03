package uz.hrlab.grading.methodology.application;

import uz.hrlab.grading.methodology.domain.Methodology;
import uz.hrlab.grading.methodology.domain.MethodologyVersion;

/**
 * Lightweight read-side aggregate returned by create + find queries — pairs
 * the long-lived {@link Methodology} with the version it was created against
 * (latest, or freshly-created v1).
 */
public record MethodologyAggregate(Methodology methodology, MethodologyVersion currentVersion) { }
