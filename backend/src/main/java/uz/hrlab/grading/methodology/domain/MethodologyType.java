package uz.hrlab.grading.methodology.domain;

/**
 * Methodology family (architecture §9 Domain Model). Drives the template
 * factor structure when {@code CreateMethodologyFromTemplateUseCase} runs.
 */
public enum MethodologyType {
    /** Classic 8-factor grading model — points total ~1000. */
    CLASSIC_8_FACTOR,
    /** Extended 11-criteria HRLab model — points total = 100% × weights. */
    EXTENDED_11_CRITERIA,
    /** Fully custom — user-defined factor set, no template. */
    CUSTOM
}
