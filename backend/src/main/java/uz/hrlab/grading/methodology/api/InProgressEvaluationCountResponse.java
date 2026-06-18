package uz.hrlab.grading.methodology.api;

/**
 * Small payload for {@code GET /api/v1/methodologies/{id}/in-progress-count} —
 * the number of not-yet-submitted (DRAFT / INCOMPLETE / COMPLETE) evaluations
 * across all versions of the methodology. Drives the deactivate/archive
 * confirmation dialog. Serializes as {@code {"in_progress_evaluation_count": N}}
 * (global SNAKE_CASE Jackson).
 */
public record InProgressEvaluationCountResponse(long inProgressEvaluationCount) { }
